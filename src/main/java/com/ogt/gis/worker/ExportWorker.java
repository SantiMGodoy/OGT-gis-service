package com.ogt.gis.worker;

import com.ogt.gis.entity.MapLayer;
import com.ogt.gis.entity.SpatialFeature;
import com.ogt.gis.entity.ExportJob;
import com.ogt.gis.repository.MapLayerRepository;
import com.ogt.gis.repository.SpatialFeatureRepository;
import com.ogt.gis.repository.ExportJobRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// ✅ Imports CORRECTOS para GeoTools 30 (interfaces desde org.geotools.api)
import org.geotools.api.data.DataStore;
import org.geotools.api.data.Transaction;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.data.SimpleFeatureStore;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;

// ✅ Implementaciones concretas (org.geotools.*)
import org.geotools.data.DefaultTransaction;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;

import org.geotools.geojson.feature.FeatureJSON;
import org.geotools.geojson.geom.GeometryJSON;

import org.geotools.referencing.CRS;

import org.locationtech.jts.geom.*;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExportWorker {

    private final MapLayerRepository layerRepository;
    private final SpatialFeatureRepository featureRepository;
    private final ExportJobRepository jobRepository;

    private static final int STORAGE_SRID = 31984;
    private static final int DECIMAL_PRECISION = 8; // Para GeoJSON

    @RabbitListener(queues = "gis.export.queue")
    public void processExport(String message) {
        log.info("▶️ ExportWorker recibió: {}", message);

        ExportJob job = null;
        try {
            // Parsear mensaje: jobId;layerCode;format
            String[] parts = message.split(";");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Formato de mensaje inválido: " + message);
            }

            UUID jobId = UUID.fromString(parts[0]);
            String layerCode = parts[1];
            String format = parts[2].toUpperCase();

            // Actualizar estado del job
            job = jobRepository.findById(jobId)
                    .orElseThrow(() -> new RuntimeException("Job no encontrado: " + jobId));

            job.setStatus("PROCESSING");
            job.setStartedAt(LocalDateTime.now());
            jobRepository.save(job);

            // Validar capa
            MapLayer layer = layerRepository.findByCode(layerCode)
                    .orElseThrow(() -> new RuntimeException("Layer no encontrada: " + layerCode));

            // Obtener features
            List<SpatialFeature> features = featureRepository.findAll();
            if (features.isEmpty()) {
                throw new RuntimeException("No hay features para exportar en la capa: " + layerCode);
            }

            // Crear archivo de salida
            File outputFile = createOutputFile(layerCode, jobId, format);

            // Exportar según formato
            exportFeatures(features, outputFile, format, layer);

            // Actualizar job como completado
            job.setStatus("COMPLETED");
            job.setFileUrl(outputFile.getAbsolutePath());
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);

            log.info("✅ Exportación completada exitosamente: {}", outputFile.getAbsolutePath());

        } catch (Exception e) {
            log.error("❌ Error en proceso de exportación", e);
            handleExportError(job, message, e);
        }
    }

    /**
     * Crea el archivo de salida según el formato
     */
    private File createOutputFile(String layerCode, UUID jobId, String format) {
        String tmpDir = System.getProperty("java.io.tmpdir");
        String fileName = String.format("export_%s_%s.%s", layerCode, jobId, format.toLowerCase());
        return new File(tmpDir, fileName);
    }

    /**
     * Enruta la exportación al método correspondiente según el formato
     */
    private void exportFeatures(List<SpatialFeature> features, File output, String format, MapLayer layer)
            throws Exception {

        switch (format) {
            case "SHP", "SHAPEFILE" -> exportToShapefile(features, output);
            case "GEOJSON", "JSON" -> exportToGeoJson(features, output);
            case "KML" -> exportToKml(features, output);
            case "DXF" -> exportToDxf(features, output);
            default -> throw new IllegalArgumentException("Formato no soportado: " + format);
        }
    }

    /**
     * Maneja errores en el proceso de exportación
     */
    private void handleExportError(ExportJob job, String message, Exception error) {
        try {
            if (job == null) {
                String[] parts = message.split(";");
                UUID jobId = UUID.fromString(parts[0]);
                job = jobRepository.findById(jobId).orElse(null);
            }

            if (job != null) {
                job.setStatus("FAILED");
                job.setErrorMessage(truncateErrorMessage(error.getMessage()));
                job.setCompletedAt(LocalDateTime.now());
                jobRepository.save(job);
            }
        } catch (Exception e) {
            log.error("❌ Error al actualizar estado de fallo del job", e);
        }
    }

    private String truncateErrorMessage(String message) {
        if (message == null) return "Error desconocido";
        return message.length() > 500 ? message.substring(0, 497) + "..." : message;
    }

    // ============================================================
    // EXPORTACIÓN A SHAPEFILE
    // ============================================================

    private void exportToShapefile(List<SpatialFeature> features, File output) throws Exception {
        log.info("📦 Iniciando exportación a Shapefile: {}", output.getName());

        ShapefileDataStoreFactory factory = new ShapefileDataStoreFactory();

        Map<String, Serializable> params = new HashMap<>();
        params.put("url", output.toURI().toURL());
        params.put("create spatial index", Boolean.TRUE);

        ShapefileDataStore dataStore = (ShapefileDataStore) factory.createNewDataStore(params);
        dataStore.setCharset(StandardCharsets.UTF_8);

        try {
            // Crear schema
            SimpleFeatureType schema = createShapefileSchema(features);
            dataStore.createSchema(schema);

            // Obtener feature store
            String typeName = dataStore.getTypeNames()[0];
            SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);

            if (!(featureSource instanceof SimpleFeatureStore featureStore)) {
                throw new IOException("El DataStore no soporta escritura");
            }

            // Escribir features con transacción
            writeFeaturesToShapefile(features, featureStore, schema);

            log.info("✅ Shapefile exportado exitosamente: {} features", features.size());

        } finally {
            dataStore.dispose();
        }
    }

    private SimpleFeatureType createShapefileSchema(List<SpatialFeature> features) throws FactoryException {
        SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
        typeBuilder.setName("features");

        // Configurar CRS
        try {
            CoordinateReferenceSystem crs = CRS.decode("EPSG:" + STORAGE_SRID, true);
            typeBuilder.setCRS(crs);
        } catch (FactoryException e) {
            log.warn("⚠️ No se pudo decodificar EPSG:{}, usando CRS por defecto", STORAGE_SRID);
        }

        // Determinar tipo de geometría dominante
        Class<? extends Geometry> geomType = determineGeometryType(features);

        typeBuilder.add("the_geom", geomType);
        typeBuilder.add("id", String.class);
        typeBuilder.add("external_id", String.class);
        typeBuilder.length(254); // Límite DBF

        return typeBuilder.buildFeatureType();
    }

    private Class<? extends Geometry> determineGeometryType(List<SpatialFeature> features) {
        if (features.isEmpty()) return Geometry.class;

        // Analizar primeros 100 features para determinar tipo
        Map<String, Integer> typeCount = new HashMap<>();
        int sampleSize = Math.min(100, features.size());

        for (int i = 0; i < sampleSize; i++) {
            Geometry geom = features.get(i).getGeom();
            if (geom != null) {
                String type = geom.getGeometryType();
                typeCount.merge(type, 1, Integer::sum);
            }
        }

        // Retornar el tipo más común
        String dominantType = typeCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Geometry");

        return switch (dominantType) {
            case "Point" -> Point.class;
            case "LineString" -> LineString.class;
            case "Polygon" -> Polygon.class;
            case "MultiPoint" -> MultiPoint.class;
            case "MultiLineString" -> MultiLineString.class;
            case "MultiPolygon" -> MultiPolygon.class;
            default -> Geometry.class;
        };
    }

    private void writeFeaturesToShapefile(List<SpatialFeature> features,
                                          SimpleFeatureStore featureStore,
                                          SimpleFeatureType schema) throws IOException {

        Transaction transaction = new DefaultTransaction("create");
        featureStore.setTransaction(transaction);

        try {
            List<SimpleFeature> simpleFeatures = new ArrayList<>(features.size());
            SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(schema);

            for (SpatialFeature spatialFeature : features) {
                featureBuilder.set("the_geom", spatialFeature.getGeom());
                featureBuilder.set("id", spatialFeature.getId().toString());
                featureBuilder.set("external_id", spatialFeature.getExternalId());

                SimpleFeature feature = featureBuilder.buildFeature(UUID.randomUUID().toString());
                simpleFeatures.add(feature);
            }

            ListFeatureCollection collection = new ListFeatureCollection(schema, simpleFeatures);
            featureStore.addFeatures(collection);

            transaction.commit();
            log.debug("✅ Transacción completada: {} features escritos", simpleFeatures.size());

        } catch (Exception e) {
            transaction.rollback();
            log.error("❌ Error escribiendo features, rollback ejecutado", e);
            throw new IOException("Error en transacción de escritura", e);
        } finally {
            transaction.close();
        }
    }

    // ============================================================
    // EXPORTACIÓN A GEOJSON
    // ============================================================

    private void exportToGeoJson(List<SpatialFeature> features, File output) throws Exception {
        log.info("🌎 Iniciando exportación a GeoJSON: {}", output.getName());

        GeometryJSON geometryJson = new GeometryJSON(DECIMAL_PRECISION);

        try (FileWriter fileWriter = new FileWriter(output, StandardCharsets.UTF_8)) {
            fileWriter.write("{\"type\":\"FeatureCollection\",\"features\":[");

            boolean first = true;
            for (SpatialFeature feature : features) {
                if (!first) {
                    fileWriter.write(",");
                }
                first = false;

                writeGeoJsonFeature(feature, geometryJson, fileWriter);
            }

            fileWriter.write("]}");
        }

        log.info("✅ GeoJSON exportado exitosamente: {} features", features.size());
    }

    private void writeGeoJsonFeature(SpatialFeature feature, GeometryJSON geometryJson, FileWriter writer)
            throws IOException {

        StringWriter geomWriter = new StringWriter();
        geometryJson.write(feature.getGeom(), geomWriter);

        writer.write("{\"type\":\"Feature\",");
        writer.write("\"id\":\"" + feature.getId() + "\",");
        writer.write("\"geometry\":");
        writer.write(geomWriter.toString());
        writer.write(",\"properties\":{");
        writer.write("\"external_id\":\"" + escapeJson(feature.getExternalId()) + "\"");
        writer.write("}}");
    }

    // ============================================================
    // EXPORTACIÓN A KML
    // ============================================================

    private void exportToKml(List<SpatialFeature> features, File output) throws Exception {
        log.info("📍 Iniciando exportación a KML: {}", output.getName());

        StringBuilder kml = new StringBuilder();
        kml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        kml.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n");
        kml.append("<Document>\n");
        kml.append("<name>").append(escapeXml(output.getName())).append("</name>\n");

        int exportedCount = 0;
        for (SpatialFeature feature : features) {
            String placemark = convertToKmlPlacemark(feature);
            if (placemark != null) {
                kml.append(placemark);
                exportedCount++;
            }
        }

        kml.append("</Document>\n");
        kml.append("</kml>");

        Files.writeString(output.toPath(), kml.toString(), StandardCharsets.UTF_8);

        log.info("✅ KML exportado exitosamente: {} de {} features", exportedCount, features.size());
    }

    private String convertToKmlPlacemark(SpatialFeature feature) {
        Geometry geom = feature.getGeom();
        if (geom == null) return null;

        StringBuilder placemark = new StringBuilder();
        placemark.append("<Placemark>\n");
        placemark.append("<name>").append(escapeXml(feature.getExternalId())).append("</name>\n");
        placemark.append("<description>ID: ").append(feature.getId()).append("</description>\n");

        String geometryKml = convertGeometryToKml(geom);
        if (geometryKml != null) {
            placemark.append(geometryKml);
            placemark.append("</Placemark>\n");
            return placemark.toString();
        }

        return null;
    }

    private String convertGeometryToKml(Geometry geom) {
        return switch (geom.getGeometryType()) {
            case "Point" -> convertPointToKml((Point) geom);
            case "LineString" -> convertLineStringToKml((LineString) geom);
            case "Polygon" -> convertPolygonToKml((Polygon) geom);
            default -> {
                log.warn("⚠️ Tipo de geometría no soportado en KML: {}", geom.getGeometryType());
                yield null;
            }
        };
    }

    private String convertPointToKml(Point point) {
        return String.format("<Point><coordinates>%s,%s</coordinates></Point>\n",
                point.getX(), point.getY());
    }

    private String convertLineStringToKml(LineString line) {
        StringBuilder coords = new StringBuilder();
        for (Coordinate coord : line.getCoordinates()) {
            if (coords.length() > 0) coords.append(" ");
            coords.append(coord.x).append(",").append(coord.y);
        }
        return String.format("<LineString><coordinates>%s</coordinates></LineString>\n", coords);
    }

    private String convertPolygonToKml(Polygon polygon) {
        StringBuilder kml = new StringBuilder("<Polygon>\n<outerBoundaryIs><LinearRing><coordinates>");

        for (Coordinate coord : polygon.getExteriorRing().getCoordinates()) {
            kml.append(coord.x).append(",").append(coord.y).append(" ");
        }

        kml.append("</coordinates></LinearRing></outerBoundaryIs>\n</Polygon>\n");
        return kml.toString();
    }

    // ============================================================
    // EXPORTACIÓN A DXF
    // ============================================================

    private void exportToDxf(List<SpatialFeature> features, File output) throws Exception {
        log.info("📐 Iniciando exportación a DXF: {}", output.getName());

        StringBuilder dxf = new StringBuilder();

        // Header
        dxf.append("0\nSECTION\n2\nHEADER\n");
        dxf.append("9\n$ACADVER\n1\nAC1015\n"); // AutoCAD 2000
        dxf.append("0\nENDSEC\n");

        // Entities
        dxf.append("0\nSECTION\n2\nENTITIES\n");

        int exportedCount = 0;
        for (SpatialFeature feature : features) {
            String entity = convertToDxfEntity(feature);
            if (entity != null) {
                dxf.append(entity);
                exportedCount++;
            }
        }

        dxf.append("0\nENDSEC\n");
        dxf.append("0\nEOF\n");

        Files.writeString(output.toPath(), dxf.toString(), StandardCharsets.UTF_8);

        log.info("✅ DXF exportado exitosamente: {} de {} features", exportedCount, features.size());
    }

    private String convertToDxfEntity(SpatialFeature feature) {
        Geometry geom = feature.getGeom();
        if (geom == null) return null;

        return switch (geom.getGeometryType()) {
            case "Point" -> convertPointToDxf((Point) geom, feature.getExternalId());
            case "LineString" -> convertLineStringToDxf((LineString) geom, feature.getExternalId());
            case "MultiPolygon" -> convertMultiPolygonToDxf((MultiPolygon) geom, feature.getExternalId());
            default -> {
                log.warn("⚠️ Tipo de geometría no soportado en DXF: {}", geom.getGeometryType());
                yield null;
            }
        };
    }

    private String convertPointToDxf(Point point, String layerName) {
        return String.format("0\nPOINT\n8\n%s\n10\n%s\n20\n%s\n",
                layerName != null ? layerName : "0",
                point.getX(),
                point.getY());
    }

    private String convertLineStringToDxf(LineString line, String layerName) {
        StringBuilder dxf = new StringBuilder();
        Coordinate[] coords = line.getCoordinates();

        for (int i = 0; i < coords.length - 1; i++) {
            dxf.append(String.format("0\nLINE\n8\n%s\n10\n%s\n20\n%s\n11\n%s\n21\n%s\n",
                    layerName != null ? layerName : "0",
                    coords[i].x,
                    coords[i].y,
                    coords[i + 1].x,
                    coords[i + 1].y));
        }

        return dxf.toString();
    }

    private String convertMultiPolygonToDxf(MultiPolygon multiPoly, String layerName) {
        StringBuilder sb = new StringBuilder();
        // Iterar sobre cada polígono dentro del MultiPolygon
        for (int i = 0; i < multiPoly.getNumGeometries(); i++) {
            Polygon poly = (Polygon) multiPoly.getGeometryN(i);
            // Convertir el anillo exterior a una polilínea cerrada
            Coordinate[] coords = poly.getExteriorRing().getCoordinates();

            sb.append("0\nLWPOLYLINE\n");
            sb.append("8\n").append(layerName != null ? layerName : "0").append("\n");
            sb.append("90\n").append(coords.length).append("\n");
            sb.append("70\n1\n"); // 1 = Closed

            for (Coordinate c : coords) {
                sb.append("10\n").append(c.x).append("\n");
                sb.append("20\n").append(c.y).append("\n");
            }
        }
        return sb.toString();
    }

    // ============================================================
    // UTILIDADES
    // ============================================================

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}