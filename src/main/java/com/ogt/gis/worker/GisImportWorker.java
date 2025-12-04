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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

            String geometryTypeAllowed =
                    layer.getGeometryType() != null ? layer.getGeometryType().toUpperCase() : null;

            String businessTarget =
                    layer.getBusinessTarget() != null ? layer.getBusinessTarget().toUpperCase() : "NONE";

            File file = new File(job.getFileUrl());

            int processed = 0;
            List<Map<String, Object>> batchForRabbit = new ArrayList<>();

            // ✅ RUTA ESPECIAL PARA KML (SIN DATASTORE)
            if (file.getName().toLowerCase().endsWith(".kml")) {
                log.info("📍 Detectado archivo KML, usando parser XML manual");

                try {
                    List<KmlPlacemark> placemarks = parseKmlManually(file);

                    for (KmlPlacemark pm : placemarks) {
                        // Crear punto JTS en WGS84 (KML siempre usa este sistema)
                        Coordinate coord = new Coordinate(pm.lon, pm.lat);
                        Point point = geometryFactory.createPoint(coord);
                        point.setSRID(4326);

                        // Mapear a DTO
                        Map<String, Object> dto = new HashMap<>();
                        dto.put("code", pm.name);

                        // Parsear description si existe
                        if (pm.description != null && !pm.description.isEmpty()) {
                            parseKmlDescription(pm.description, dto);
                        }

                        // Coordenadas (mantenemos en WGS84)
                        dto.put("wgsLat", pm.lat);
                        dto.put("wgsLon", pm.lon);
                        dto.put("sirgasX", pm.lon);
                        dto.put("sirgasY", pm.lat);
                        dto.put("srid", 4326);

                        batchForRabbit.add(dto);

                        // Enviar batch cada 50
                        if (batchForRabbit.size() >= 50) {
                            log.info("📤 Enviando batch de {} puntos a RabbitMQ", batchForRabbit.size());
                            rabbitTemplate.convertAndSend(
                                    LIGHTPOINT_EXCHANGE,
                                    LIGHTPOINT_IMPORT_KEY,
                                    batchForRabbit);
                            log.info("✅ Batch enviado correctamente");
                            processed += batchForRabbit.size();
                            batchForRabbit.clear();
                        }
                    }

                    // Enviar resto del batch
                    if (!batchForRabbit.isEmpty()) {
                        log.info("📤 Enviando último batch de {} puntos a RabbitMQ", batchForRabbit.size());
                        rabbitTemplate.convertAndSend(
                                LIGHTPOINT_EXCHANGE,
                                LIGHTPOINT_IMPORT_KEY,
                                batchForRabbit);
                        log.info("✅ Último batch enviado correctamente");
                        processed += batchForRabbit.size();
                    }

                    job.setStatus("COMPLETED");
                    job.setRowsProcessed(processed);
                    job.setCompletedAt(LocalDateTime.now());
                    jobRepository.save(job);

                    log.info("✅ Importación KML completada. Registros procesados: {}", processed);
                    return; // ← IMPORTANTE: salir aquí para no procesar con DataStore

                } catch (Exception e) {
                    log.error("❌ Error procesando KML", e);
                    job.setStatus("FAILED");
                    job.setErrorMessage(e.getMessage());
                    job.setCompletedAt(LocalDateTime.now());
                    jobRepository.save(job);
                    return;
                }
            }

            // ✅ RUTA NORMAL PARA SHAPEFILES Y OTROS FORMATOS
            Map<String, Object> params = new HashMap<>();
            params.put("url", file.toURI().toURL());
            DataStore dataStore = DataStoreFinder.getDataStore(params);

            if (dataStore == null)
                throw new RuntimeException("No se pudo abrir el archivo (formato no soportado)");

            String typeName = dataStore.getTypeNames()[0];
            FeatureSource<SimpleFeatureType, SimpleFeature> source =
                    dataStore.getFeatureSource(typeName);

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
                                log.info("📤 Enviando batch de {} puntos a RabbitMQ", batchForRabbit.size());
                                rabbitTemplate.convertAndSend(
                                        LIGHTPOINT_EXCHANGE,
                                        LIGHTPOINT_IMPORT_KEY,
                                        batchForRabbit);
                                log.info("✅ Batch enviado correctamente");
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
                log.info("📤 Enviando último batch de {} puntos a RabbitMQ", batchForRabbit.size());
                rabbitTemplate.convertAndSend(
                        LIGHTPOINT_EXCHANGE,
                        LIGHTPOINT_IMPORT_KEY,
                        batchForRabbit);
                log.info("✅ Último batch enviado correctamente");
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

    // ✅ NUEVO: Parser manual de KML usando DOM
    private List<KmlPlacemark> parseKmlManually(File kmlFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        org.w3c.dom.Document doc = builder.parse(kmlFile);

        NodeList placemarks = doc.getElementsByTagName("Placemark");
        List<KmlPlacemark> result = new ArrayList<>();

        for (int i = 0; i < placemarks.getLength(); i++) {
            Element placemark = (Element) placemarks.item(i);

            try {
                String name = getXmlTagValue("name", placemark);
                String description = getXmlTagValue("description", placemark);
                String coordsStr = getXmlTagValue("coordinates", placemark);

                if (coordsStr == null || coordsStr.isEmpty()) {
                    log.warn("⚠️ Placemark sin coordenadas: {}", name);
                    continue;
                }

                // Parsear coordenadas: "lon,lat,alt" o "lon,lat"
                String[] coords = coordsStr.trim().split(",");
                if (coords.length < 2) {
                    log.warn("⚠️ Formato de coordenadas inválido: {}", coordsStr);
                    continue;
                }

                double lon = Double.parseDouble(coords[0].trim());
                double lat = Double.parseDouble(coords[1].trim());

                result.add(new KmlPlacemark(name, description, lon, lat));

            } catch (Exception e) {
                log.error("❌ Error parseando placemark {}: {}", i, e.getMessage());
            }
        }

        log.info("✅ Parseados {} placemarks del KML", result.size());
        return result;
    }

    private String getXmlTagValue(String tag, Element element) {
        NodeList nodeList = element.getElementsByTagName(tag);
        if (nodeList.getLength() > 0) {
            Node node = nodeList.item(0);
            if (node != null && node.getFirstChild() != null) {
                return node.getFirstChild().getNodeValue();
            }
        }
        return null;
    }

    // ✅ Parsear el campo "description" del KML
    private void parseKmlDescription(String description, Map<String, Object> dto) {
        try {
            // Ejemplo: "0129301720 - ID: 129301720 | Tipo da Lâmpada: FLU0020 | Quantidade: 2 | ..."

            // Extraer código (nombre del placemark)
            if (!dto.containsKey("code") || dto.get("code") == null) {
                Pattern codePattern = Pattern.compile("^(\\d+)");
                Matcher codeMatcher = codePattern.matcher(description);
                if (codeMatcher.find()) {
                    dto.put("code", codeMatcher.group(1));
                }
            }

            // Extraer tipo de lámpara
            Pattern lampPattern = Pattern.compile("Tipo da Lâmpada:\\s*([A-Z0-9]+)", Pattern.CASE_INSENSITIVE);
            Matcher lampMatcher = lampPattern.matcher(description);
            if (lampMatcher.find()) {
                dto.put("lampType", lampMatcher.group(1).toUpperCase());
            }

            // Extraer cantidad
            Pattern qtyPattern = Pattern.compile("Quantidade:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
            Matcher qtyMatcher = qtyPattern.matcher(description);
            if (qtyMatcher.find()) {
                dto.put("lampQuantity", Integer.parseInt(qtyMatcher.group(1)));
            }

            // Extraer município
            Pattern munPattern = Pattern.compile("Município:\\s*([A-ZÀ-Ú\\s]+)", Pattern.CASE_INSENSITIVE);
            Matcher munMatcher = munPattern.matcher(description);
            if (munMatcher.find()) {
                dto.put("district", munMatcher.group(1).trim());
            }

            log.debug("✅ Parseado description KML: code={}, lampType={}, quantity={}, district={}",
                    dto.get("code"), dto.get("lampType"), dto.get("lampQuantity"), dto.get("district"));

        } catch (Exception e) {
            log.warn("⚠️ Error parseando description del KML: {}", e.getMessage());
        }
    }

    // ✅ Helper para extraer atributos de forma segura
    private String extractAttribute(SimpleFeature feature, String attributeName) {
        try {
            Object attr = feature.getAttribute(attributeName);
            return attr != null ? attr.toString() : null;
        } catch (Exception e) {
            return null;
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

    /**
     * Clase interna para representar un placemark del KML
     */
    private static class KmlPlacemark {
        String name;
        String description;
        double lon;
        double lat;

        KmlPlacemark(String name, String description, double lon, double lat) {
            this.name = name;
            this.description = description;
            this.lon = lon;
            this.lat = lat;
        }
    }
}