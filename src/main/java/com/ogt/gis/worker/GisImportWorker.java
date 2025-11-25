package com.ogt.gis.worker;

import com.ogt.gis.entity.ImportJob;
import com.ogt.gis.entity.MapLayer;
import com.ogt.gis.entity.SpatialFeature;
import com.ogt.gis.repository.ImportJobRepository;
import com.ogt.gis.repository.MapLayerRepository;
import com.ogt.gis.repository.SpatialFeatureRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.geotools.api.data.DataStore;
import org.geotools.api.data.DataStoreFinder;
import org.geotools.api.data.FeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;

import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;

import org.locationtech.jts.geom.Geometry;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class GisImportWorker {

    private final ImportJobRepository jobRepository;
    private final MapLayerRepository layerRepository;
    private final SpatialFeatureRepository featureRepository;

    private static final int STORAGE_SRID = 31984;

    @RabbitListener(queues = "gis.import.queue")
    @Transactional
    public void processImport(String message) {

        log.info("▶️ GIS ImportWorker recibió: {}", message);

        try {
            // -----------------------------
            // PARSEAR EL MENSAJE
            // -----------------------------
            String[] parts = message.split(";");
            UUID jobId = UUID.fromString(parts[0]);
            String layerCode = parts.length > 1 ? parts[1] : null;

            ImportJob job = jobRepository.findById(jobId).orElse(null);
            if (job == null) {
                log.error("❌ ImportJob {} no encontrado", jobId);
                return;
            }

            job.setStatus("PROCESSING");
            job.setStartedAt(LocalDateTime.now());
            jobRepository.save(job);

            // Capa destino
            MapLayer layer = layerRepository.findByCode(layerCode)
                    .orElseThrow(() -> new RuntimeException("Layer no encontrada: " + layerCode));

            // Archivo
            File shp = new File(job.getFileUrl());
            if (!shp.exists())
                throw new RuntimeException("Archivo inexistente: " + shp.getAbsolutePath());

            // -----------------------------
            // ABRIR SHAPEFILE
            // -----------------------------
            Map<String, Object> params = new HashMap<>();
            params.put("url", shp.toURI().toURL());

            DataStore dataStore = DataStoreFinder.getDataStore(params);
            if (dataStore == null)
                throw new RuntimeException("No se pudo abrir el shapefile — formato inválido/corrupto");

            String typeName = dataStore.getTypeNames()[0];

            FeatureSource<SimpleFeatureType, SimpleFeature> source =
                    dataStore.getFeatureSource(typeName);
            SimpleFeatureType schema = source.getSchema();

            CoordinateReferenceSystem sourceCRS = schema.getCoordinateReferenceSystem();
            Integer detectedSRID = null;

            // Detectar SRID automáticamente
            if (sourceCRS != null) {
                try {
                    String code = CRS.toSRS(sourceCRS, true);
                    if (code != null && code.startsWith("EPSG:")) {
                        detectedSRID = Integer.parseInt(code.split(":")[1]);
                    }
                } catch (Exception ignored) {
                }
            }

            // fallback desde parámetros del job
            if (detectedSRID == null && job.getParameters() != null) {
                String p = job.getParameters().toUpperCase();
                if (p.contains("SRID:")) {
                    try {
                        String after = p.substring(p.indexOf("SRID:") + 5).trim();
                        detectedSRID = Integer.parseInt(after.split("[,\\s]")[0]);
                    } catch (Exception ignored) {
                    }
                }
            }

            if (detectedSRID == null) {
                detectedSRID = STORAGE_SRID;
                log.warn("⚠ No se detectó SRID del archivo. Se asume {}", STORAGE_SRID);
            }

            // -----------------------------
            // CONFIGURAR REPROYECCIÓN
            // -----------------------------
            MathTransform transform = null;

            if (!Objects.equals(detectedSRID, STORAGE_SRID)) {
                CoordinateReferenceSystem src = CRS.decode("EPSG:" + detectedSRID, true);
                CoordinateReferenceSystem dst = CRS.decode("EPSG:" + STORAGE_SRID, true);

                transform = CRS.findMathTransform(src, dst, true);

                log.info("📌 Reproyección activada EPSG:{} → EPSG:{}", detectedSRID, STORAGE_SRID);
            } else {
                log.info("📌 El archivo ya está en el SRID objetivo {}", STORAGE_SRID);
            }

            // -----------------------------
            // LECTURA DE FEATURES
            // -----------------------------
            FeatureCollection<SimpleFeatureType, SimpleFeature> collection = source.getFeatures();

            int count = 0;
            List<SpatialFeature> buffer = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            try (FeatureIterator<SimpleFeature> it = collection.features()) {
                while (it.hasNext()) {
                    SimpleFeature f = it.next();

                    Object geomObj = f.getDefaultGeometry();
                    if (geomObj == null) {
                        errors.add("Feature sin geometría: " + f.getID());
                        continue;
                    }

                    Geometry geom = (Geometry) geomObj;

                    // Reproyección → si corresponde
                    if (transform != null) {
                        try {
                            geom = JTS.transform(geom, transform);
                        } catch (Exception ex) {
                            errors.add("Error reproyectando " + f.getID() + ": " + ex.getMessage());
                            continue;
                        }
                    }

                    // Validación
                    if (!geom.isValid()) {
                        try {
                            geom = geom.buffer(0);
                        } catch (Exception ignored) {
                        }
                    }
                    if (!geom.isValid()) {
                        errors.add("Geometría inválida: " + f.getID());
                        continue;
                    }

                    // Asignar SRID
                    geom.setSRID(STORAGE_SRID);

                    // Serializar atributos simples (opcional)
                    Map<String, Object> props = new HashMap<>();
                    f.getProperties().forEach(p -> props.put(p.getName().toString(), p.getValue()));
                    String jsonProps = props.toString();

                    SpatialFeature sf = SpatialFeature.builder()
                            .layer(layer)
                            .externalId(f.getID())
                            .geom(geom)
                            .properties(jsonProps)
                            .build();

                    buffer.add(sf);

                    if (buffer.size() >= 1000) {
                        featureRepository.saveAll(buffer);
                        count += buffer.size();
                        buffer.clear();
                    }
                }
            }

            if (!buffer.isEmpty()) {
                featureRepository.saveAll(buffer);
                count += buffer.size();
            }

            dataStore.dispose();

            // -----------------------------
            // ACTUALIZAR JOB
            // -----------------------------
            job.setStatus("COMPLETED");
            job.setRowsProcessed(count);
            job.setCompletedAt(LocalDateTime.now());

            if (!errors.isEmpty()) {
                job.setErrorMessage("Errores: " + errors.size() +
                        " Ejemplos: " + errors.stream().limit(3).toList());
            }

            jobRepository.save(job);

            log.info("✅ Import COMPLETO. {} features importados. {} errores.", count, errors.size());

        } catch (Exception e) {

            log.error("❌ Fallo en importación", e);

            try {
                String[] parts = message.split(";");
                UUID jobId = UUID.fromString(parts[0]);
                ImportJob job = jobRepository.findById(jobId).orElse(null);
                if (job != null) {
                    job.setStatus("FAILED");
                    job.setErrorMessage(e.getMessage());
                    job.setCompletedAt(LocalDateTime.now());
                    jobRepository.save(job);
                }
            } catch (Exception ignored) {
            }
        }
    }
}
