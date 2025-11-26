package com.ogt.gis.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ogt.gis.entity.DistrictBoundary;
import com.ogt.gis.entity.ImportJob;
import com.ogt.gis.entity.MapLayer;
import com.ogt.gis.entity.SpatialFeature;
import com.ogt.gis.repository.DistrictBoundaryRepository;
import com.ogt.gis.repository.ImportJobRepository;
import com.ogt.gis.repository.MapLayerRepository;
import com.ogt.gis.repository.SpatialFeatureRepository;
import com.ogt.gis.service.CoordinateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.geotools.api.data.DataStore;
import org.geotools.api.data.DataStoreFinder;
import org.geotools.api.data.FeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.feature.FeatureIterator;
import org.locationtech.jts.geom.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class GisImportWorker {

    private final ImportJobRepository jobRepository;
    private final MapLayerRepository layerRepository;
    private final SpatialFeatureRepository featureRepository;
    private final DistrictBoundaryRepository districtRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final CoordinateService coordinateService;

    private static final String LIGHTPOINT_EXCHANGE = "ogt.lightpoint.events";
    private static final String LIGHTPOINT_IMPORT_KEY = "lightpoint.import.batch";
    private static final GeometryFactory geometryFactory = new GeometryFactory();

    @RabbitListener(queues = "gis.import.queue")
    @Transactional
    public void processImport(String message) {
        log.info("▶️ [GIS Import Worker] Recibido: {}", message);

        String[] parts = message.split(";");
        if (parts.length < 2) {
            log.error("❌ Mensaje inválido en cola: {}", message);
            return;
        }

        UUID jobId = UUID.fromString(parts[0]);
        String layerCode = parts[1];

        ImportJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return;

        try {
            job.setStatus("PROCESSING");
            job.setStartedAt(LocalDateTime.now());
            jobRepository.save(job);

            MapLayer layer = layerRepository.findByCode(layerCode)
                    .orElseThrow(() -> new RuntimeException("La capa no está registrada: " + layerCode));

            int expectedSRID = layer.getSrid() != null
                    ? layer.getSrid()
                    : coordinateService.detectUTMZone(-40.3);

            // ✅ SE DECLARA UNA SOLA VEZ AQUÍ
            String geometryTypeAllowed =
                    layer.getGeometryType() != null ? layer.getGeometryType().toUpperCase() : null;

            String businessTarget =
                    layer.getBusinessTarget() != null ? layer.getBusinessTarget().toUpperCase() : "NONE";

            File file = new File(job.getFileUrl());
            Map<String, Object> params = new HashMap<>();
            params.put("url", file.toURI().toURL());

            DataStore dataStore = DataStoreFinder.getDataStore(params);
            if (dataStore == null)
                throw new RuntimeException("No se pudo abrir el archivo (formato no soportado)");

            String typeName = dataStore.getTypeNames()[0];
            FeatureSource<SimpleFeatureType, SimpleFeature> source =
                    dataStore.getFeatureSource(typeName);

            int processed = 0;
            List<Map<String, Object>> batchForRabbit = new ArrayList<>();

            try (FeatureIterator<SimpleFeature> features = source.getFeatures().features()) {

                while (features.hasNext()) {
                    SimpleFeature f = features.next();
                    Geometry geom = (Geometry) f.getDefaultGeometry();

                    if (geom == null) continue;

                    // --- VALIDAR TIPO DE GEOMETRÍA ---
                    if (geometryTypeAllowed != null &&
                            !geom.getGeometryType().equalsIgnoreCase(geometryTypeAllowed)) {

                        log.warn("⚠️ Geometría incompatible en capa {}: {} (esperado: {})",
                                layerCode, geom.getGeometryType(), geometryTypeAllowed);
                        continue;
                    }

                    // --- REPROYECCIÓN ---
                    if (geom.getSRID() == 0) geom.setSRID(expectedSRID);
                    geom = ensureSRID(geom, expectedSRID);

                    // --- RUTEO POR TARGET ---
                    switch (businessTarget) {

                        case "LIGHT_POINT_SERVICE":
                            if (geom instanceof Point) {
                                Map<String, Object> dto = mapAttributes(f, layer.getAttributeMapping());
                                dto.put("sirgasX", ((Point) geom).getX());
                                dto.put("sirgasY", ((Point) geom).getY());
                                dto.put("srid", expectedSRID);
                                batchForRabbit.add(dto);
                            }
                            if (batchForRabbit.size() >= 50) {
                                rabbitTemplate.convertAndSend(
                                        LIGHTPOINT_EXCHANGE, LIGHTPOINT_IMPORT_KEY, batchForRabbit);
                                processed += batchForRabbit.size();
                                batchForRabbit.clear();
                            }
                            break;

                        case "DISTRICTS":
                            saveDistrict(f, geom);
                            processed++;
                            break;

                        default: // NONE → referencia
                            saveSpatialFeature(f, geom, layer);
                            processed++;
                    }
                }
            }

            // ENVIAR RESTO DEL BATCH
            if (!batchForRabbit.isEmpty()) {
                rabbitTemplate.convertAndSend(LIGHTPOINT_EXCHANGE, LIGHTPOINT_IMPORT_KEY, batchForRabbit);
                processed += batchForRabbit.size();
            }

            dataStore.dispose();

            job.setStatus("COMPLETED");
            job.setRowsProcessed(processed);
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);

            log.info("✅ Importación completada para capa {}. Registros procesados: {}", layerCode, processed);

        } catch (Exception e) {
            log.error("❌ Error crítico en importación", e);
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);
        }
    }

    // ---------------------------------------------------------------------
    // HELPERS
    // ---------------------------------------------------------------------

    private Geometry ensureSRID(Geometry geom, int expectedSRID) {
        try {
            geom.setSRID(expectedSRID);
            return geom;
        } catch (Exception e) {
            log.warn("No se pudo asignar SRID {}, usando original {}", expectedSRID, geom.getSRID());
            return geom;
        }
    }

    private void saveDistrict(SimpleFeature f, Geometry geom) {
        DistrictBoundary d = DistrictBoundary.builder()
                .code(f.getID())
                .name(extractName(f))
                .geom(geom)
                .area(geom.getArea())
                .build();

        districtRepository.save(d);
    }

    private void saveSpatialFeature(SimpleFeature f, Geometry geom, MapLayer layer) {
        SpatialFeature sf = SpatialFeature.builder()
                .layer(layer)
                .externalId(f.getID())
                .geom(geom)
                .properties(extractPropertiesJson(f))
                .build();

        featureRepository.save(sf);
    }

    private String extractName(SimpleFeature f) {
        Object nameAttr = f.getAttribute("NAME");
        return nameAttr != null ? nameAttr.toString() : "Unnamed";
    }

    private String extractPropertiesJson(SimpleFeature f) {
        Map<String, Object> props = new LinkedHashMap<>();

        for (org.geotools.api.feature.type.PropertyDescriptor pd :
                f.getType().getDescriptors()) {

            String key = pd.getName().toString();
            if ("the_geom".equals(key)) continue;

            Object val = f.getAttribute(key);
            if (val != null) props.put(key, val);
        }

        try {
            return objectMapper.writeValueAsString(props);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> mapAttributes(SimpleFeature f, String mappingJson) {
        Map<String, Object> dto = new HashMap<>();

        try {
            if (mappingJson != null && !mappingJson.isBlank()) {
                Map<String, String> mapping = objectMapper.readValue(mappingJson, Map.class);

                for (var entry : mapping.entrySet()) {
                    String shpField = entry.getKey();
                    String dtoField = entry.getValue();
                    Object value = f.getAttribute(shpField);
                    if (value != null) dto.put(dtoField, value);
                }

            } else {
                dto.put("code", f.getID());
            }

        } catch (Exception e) {
            log.warn("⚠️ Error en mapeo dinámico de atributos", e);
        }

        return dto;
    }
}
