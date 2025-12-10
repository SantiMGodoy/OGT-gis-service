package com.ogt.gis.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ogt.gis.entity.*;
import com.ogt.gis.repository.*;
import com.ogt.gis.service.CoordinateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import java.io.FileInputStream;
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

            String geometryTypeAllowed = layer.getGeometryType() != null ? layer.getGeometryType().toUpperCase() : null;
            String businessTarget = layer.getBusinessTarget() != null ? layer.getBusinessTarget().toUpperCase() : "NONE";

            File file = new File(job.getFileUrl());
            int processed = 0;
            List<Map<String, Object>> batchForRabbit = new ArrayList<>();

            // =================================================================================
            // 📊 RUTA 1: ARCHIVOS XLSX
            // =================================================================================
            if (file.getName().toLowerCase().endsWith(".xlsx") || file.getName().toLowerCase().endsWith(".xls")) {
                log.info("📊 Detectado archivo Excel, procesando con Apache POI");

                try (FileInputStream fis = new FileInputStream(file);
                     Workbook workbook = new XSSFWorkbook(fis)) {

                    Sheet sheet = workbook.getSheetAt(0);

                    // 🔍 BÚSQUEDA INTELIGENTE DE ENCABEZADOS
                    int headerRowIndex = -1;
                    for (int i = 0; i <= sheet.getLastRowNum() && i < 50; i++) {
                        Row row = sheet.getRow(i);
                        if (row != null && row.getPhysicalNumberOfCells() > 0) {
                            headerRowIndex = i;
                            break;
                        }
                    }

                    if (headerRowIndex == -1) {
                        throw new RuntimeException("El archivo Excel parece estar vacío o no tiene encabezados detectables");
                    }

                    Row headerRow = sheet.getRow(headerRowIndex);

                    // 1. Construir mapa de columnas normalizado
                    Map<String, Integer> columnMap = buildColumnMap(headerRow);
                    log.info("📊 Columnas detectadas: {}", columnMap.keySet());

                    // 2. Iterar filas de datos
                    for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
                        Row row = sheet.getRow(i);
                        if (row == null) continue;

                        try {
                            // 🟢 FIX: Usar nombres completos exactos (Y=Lat, X=Lon)
                            // LATITUD (Y) -> Ejemplo: -20.363...
                            Double lat = getCoordinateValue(row, columnMap,
                                    "COORDENADA_Y_LATLONG", // <--- PRIORIDAD 1
                                    "coordenada_y_latlo", "latitude", "lat", "latitud", "y", "coordenada_y");

                            // LONGITUD (X) -> Ejemplo: -40.426...
                            Double lon = getCoordinateValue(row, columnMap,
                                    "COORDENADA_X_LATLONG", // <--- PRIORIDAD 1
                                    "coordenada_x_latlo", "longitude", "lon", "longitud", "lng", "x", "coordenada_x");

                            if (lat == null || lon == null) {
                                log.debug("⚠️ Fila {} sin coordenadas válidas (Lat/Lon nulos)", i);
                                continue;
                            }

                            // 🚨 CHECK DE SEGURIDAD PARA BRASIL 🚨
                            // Latitud debe ser aprox -20 y Longitud aprox -40.
                            // Si Latitud es "más negativa" que Longitud (ej: Lat -40, Lon -20), están invertidas.
                            // (En valor absoluto: |Lat| < |Lon| en esa zona de Brasil)
                            if (Math.abs(lat) > Math.abs(lon)) {
                                log.debug("🔄 Invirtiendo Lat/Lon detectado (Lat: {}, Lon: {})", lat, lon);
                                double temp = lat;
                                lat = lon;
                                lon = temp;
                            }

                            Map<String, Object> dto = buildLightPointDTOFromExcel(row, columnMap, lat, lon);
                            batchForRabbit.add(dto);

                            if (batchForRabbit.size() >= 50) {
                                sendBatchToRabbitMQ(batchForRabbit);
                                processed += batchForRabbit.size();
                                batchForRabbit.clear();
                            }

                        } catch (Exception e) {
                            log.warn("⚠️ Error procesando fila Excel {}: {}", i, e.getMessage());
                        }
                    }

                    if (!batchForRabbit.isEmpty()) {
                        sendBatchToRabbitMQ(batchForRabbit);
                        processed += batchForRabbit.size();
                    }

                    completeJob(job, processed);
                    log.info("✅ Importación XLSX completada. Registros procesados: {}", processed);
                    return;

                } catch (Exception e) {
                    log.error("❌ Error procesando Excel", e);
                    handleImportError(job, e);
                    return;
                }
            }

            // =================================================================================
            // 📍 RUTA 2: ARCHIVOS KML (Sin cambios)
            // =================================================================================
            if (file.getName().toLowerCase().endsWith(".kml")) {
                try {
                    List<KmlPlacemark> placemarks = parseKmlWithTable(file);
                    for (KmlPlacemark pm : placemarks) {
                        Map<String, Object> dto = buildLightPointDTO(pm);
                        batchForRabbit.add(dto);
                        if (batchForRabbit.size() >= 50) {
                            sendBatchToRabbitMQ(batchForRabbit);
                            processed += batchForRabbit.size();
                            batchForRabbit.clear();
                        }
                    }
                    if (!batchForRabbit.isEmpty()) {
                        sendBatchToRabbitMQ(batchForRabbit);
                        processed += batchForRabbit.size();
                    }
                    completeJob(job, processed);
                    return;
                } catch (Exception e) {
                    handleImportError(job, e);
                    return;
                }
            }

            // =================================================================================
            // 📍 RUTA 3: SHAPEFILES (Sin cambios)
            // =================================================================================
            Map<String, Object> params = new HashMap<>();
            params.put("url", file.toURI().toURL());
            DataStore dataStore = DataStoreFinder.getDataStore(params);
            if (dataStore != null) {
                String typeName = dataStore.getTypeNames()[0];
                FeatureSource<SimpleFeatureType, SimpleFeature> source = dataStore.getFeatureSource(typeName);
                try (FeatureIterator<SimpleFeature> features = source.getFeatures().features()) {
                    while (features.hasNext()) {
                        SimpleFeature f = features.next();
                        Geometry geom = (Geometry) f.getDefaultGeometry();
                        if (geom == null) continue;
                        if (geom.getSRID() == 0) geom.setSRID(expectedSRID);
                        geom = ensureSRID(geom, expectedSRID);

                        if (businessTarget.equals("LIGHT_POINT_SERVICE") && geom instanceof Point) {
                            Map<String, Object> dto = mapAttributes(f, layer.getAttributeMapping());
                            dto.put("sirgasX", ((Point) geom).getX());
                            dto.put("sirgasY", ((Point) geom).getY());
                            dto.put("srid", expectedSRID);
                            batchForRabbit.add(dto);
                        } else if (businessTarget.equals("DISTRICTS")) {
                            saveDistrict(f, geom);
                            processed++;
                        } else {
                            saveSpatialFeature(f, geom, layer);
                            processed++;
                        }
                        if (batchForRabbit.size() >= 50) {
                            sendBatchToRabbitMQ(batchForRabbit);
                            processed += batchForRabbit.size();
                            batchForRabbit.clear();
                        }
                    }
                }
                if (!batchForRabbit.isEmpty()) {
                    sendBatchToRabbitMQ(batchForRabbit);
                    processed += batchForRabbit.size();
                }
                dataStore.dispose();
                completeJob(job, processed);
            }

        } catch (Exception e) {
            log.error("❌ Error crítico en importación", e);
            handleImportError(job, e);
        }
    }

    // =================================================================================
    // 🛠️ MÉTODOS AUXILIARES: PARSER EXCEL
    // =================================================================================

    private Map<String, Integer> buildColumnMap(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        for (Cell cell : headerRow) {
            String rawValue = getCellValueAsString(cell);
            if (rawValue == null || rawValue.isBlank()) continue;
            // Normaliza quitando todo lo que no sea alfanumérico
            String header = rawValue.replaceAll("[\n\r]+", " ").replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
            map.put(header, cell.getColumnIndex());
        }
        return map;
    }

    private Map<String, Object> buildLightPointDTOFromExcel(Row row, Map<String, Integer> columnMap, Double lat, Double lon) {
        Map<String, Object> dto = new HashMap<>();

        // Identificadores
        String code = getFromRow(row, columnMap, "id", "id da lampada", "code", "codigo", "id_da_lampada");
        if (code == null || code.isBlank()) code = "EXCEL-" + UUID.randomUUID().toString().substring(0, 8);
        dto.put("code", code);

        // Ubicación
        String logradouro = getFromRow(row, columnMap, "nome logradouro", "logradouro", "direccion", "endereco", "nome_logradouro");
        String bairro = getFromRow(row, columnMap, "nome bairro", "bairro", "barrio", "district", "nome_bairro");
        String municipio = getFromRow(row, columnMap, "nome municipio", "municipio", "ciudad", "city", "nome_municipio");

        dto.put("address", logradouro != null ? logradouro : "Dirección no especificada");
        dto.put("district", bairro);
        dto.put("city", municipio);
        dto.put("reference", buildExcelAddress(logradouro, bairro, municipio));

        // Coordenadas WGS84 (Lat, Lon)
        dto.put("wgsLat", lat);
        dto.put("wgsLon", lon);
        dto.put("srid", 4326);

        // Componente: LÁMPARA
        String tipo = getFromRow(row, columnMap, "tipo lampada", "tipo_lampada_da", "tipo", "tipo_lampada");
        String tipoInstalada = getFromRow(row, columnMap, "tipo de lampada instalada", "tipo_lampada_instalada");
        String potencia = getFromRow(row, columnMap, "potencia", "potencia da lampada substituida", "potencia_lampada");
        String luminaria = getFromRow(row, columnMap, "luminaria", "modelo_luminaria", "armadura");

        if (tipo != null || tipoInstalada != null || potencia != null || luminaria != null) {
            Map<String, Object> component = new HashMap<>();
            component.put("type", "LAMPADA");

            // Simplificación solicitada: Tipo siempre LED si no es explícitamente otro
            String rawType = tipoInstalada != null ? tipoInstalada : tipo;
            String manufacturer = extractManufacturer(rawType);
            component.put("lampManufacturer", manufacturer);
            component.put("lampType", manufacturer);

            // Modelo: Prioridad LUMINARIA, sino el tipo instalado
            String model = luminaria != null ? luminaria : rawType;
            component.put("model", model);

            Integer power = parsePower(potencia);
            if (power != null) component.put("lampPower", power);

            component.put("status", "FUNCIONAL");
            dto.put("components", List.of(component));
        }

        return dto;
    }

    private Double getCoordinateValue(Row row, Map<String, Integer> columnMap, String... possibleNames) {
        for (String name : possibleNames) {
            // Normalizamos la key de búsqueda igual que las del mapa (sin guiones bajos, lowercase)
            String searchKey = name.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
            Integer colIndex = columnMap.get(searchKey);
            if (colIndex != null) {
                Cell cell = row.getCell(colIndex);
                if (cell != null) {
                    try {
                        if (cell.getCellType() == CellType.NUMERIC) {
                            return cell.getNumericCellValue();
                        } else if (cell.getCellType() == CellType.STRING) {
                            String val = cell.getStringCellValue().trim();
                            val = val.replaceAll("[^0-9.,-]", "");
                            return Double.parseDouble(val.replace(",", "."));
                        }
                    } catch (Exception e) { /* ignore */ }
                }
            }
        }
        return null;
    }

    private String getFromRow(Row row, Map<String, Integer> columnMap, String... possibleKeys) {
        for (String key : possibleKeys) {
            String searchKey = key.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
            Integer colIndex = columnMap.get(searchKey);
            if (colIndex != null) {
                Cell cell = row.getCell(colIndex);
                return getCellValueAsString(cell);
            }
        }
        return null;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) return cell.getDateCellValue().toString();
                double num = cell.getNumericCellValue();
                return (num == (long) num) ? String.valueOf((long) num) : String.valueOf(num);
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try { return String.valueOf(cell.getNumericCellValue()); }
                catch (Exception e) { return cell.getStringCellValue(); }
            default: return null;
        }
    }

    private String buildExcelAddress(String logradouro, String bairro, String municipio) {
        StringBuilder sb = new StringBuilder();
        if (logradouro != null && !logradouro.trim().isEmpty()) sb.append(logradouro);
        if (bairro != null && !bairro.trim().isEmpty()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(bairro);
        }
        if (municipio != null && !municipio.trim().isEmpty()) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(municipio);
        }
        return sb.length() > 0 ? sb.toString() : "Dirección no especificada";
    }

    // =================================================================================
    // 🛠️ MÉTODOS AUXILIARES: GENERAL
    // =================================================================================

    private String extractManufacturer(String tipo) {
        if (tipo == null) return "LED";
        String upper = tipo.toUpperCase();

        if (upper.contains("LED")) return "LED";
        if (upper.contains("VAPOR") || upper.contains("SODIO") || upper.contains("VSAP")) return "Vapor de Sodio";
        if (upper.contains("MET") || upper.contains("METAL")) return "Vapor Metalico";

        return "LED"; // Default
    }

    private Integer parsePower(String powerStr) {
        if (powerStr == null) return null;
        try {
            String nums = powerStr.replaceAll("[^0-9]", "");
            return nums.isEmpty() ? null : Integer.parseInt(nums);
        } catch (NumberFormatException e) { return null; }
    }

    // ... (Métodos de KML, GeoTools y RabbitMQ se mantienen igual que en la versión anterior) ...
    // Solo pego los necesarios para que compile completo si usas solo este bloque

    private List<KmlPlacemark> parseKmlWithTable(File kmlFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        org.w3c.dom.Document doc;
        try { doc = builder.parse(kmlFile); }
        catch (org.xml.sax.SAXParseException e) {
            try (FileInputStream fis = new FileInputStream(kmlFile);
                 java.io.InputStreamReader isr = new java.io.InputStreamReader(fis, "ISO-8859-1")) {
                org.xml.sax.InputSource is = new org.xml.sax.InputSource(isr);
                doc = builder.parse(is);
            }
        }
        NodeList placemarks = doc.getElementsByTagName("Placemark");
        List<KmlPlacemark> result = new ArrayList<>();
        for (int i = 0; i < placemarks.getLength(); i++) {
            Element placemark = (Element) placemarks.item(i);
            try {
                String name = getXmlTagValue("name", placemark);
                String coordsStr = getXmlTagValue("coordinates", placemark);
                if (coordsStr == null || coordsStr.isEmpty()) continue;
                String[] coords = coordsStr.trim().split(",");
                if (coords.length < 2) continue;
                double lon = Double.parseDouble(coords[0].trim());
                double lat = Double.parseDouble(coords[1].trim());
                if (Math.abs(lat) > Math.abs(lon)) { double temp = lat; lat = lon; lon = temp; }
                KmlPlacemark pm = new KmlPlacemark(name, lon, lat);
                String desc = getXmlTagValue("description", placemark);
                if (desc != null) parseTableData(desc, pm);
                result.add(pm);
            } catch (Exception e) {}
        }
        return result;
    }

    private void parseTableData(String html, KmlPlacemark pm) {
        try {
            pm.idLampada = extractTableValue(html, "ID DA LÂMPADA");
            pm.tipoLampada = extractTableValue(html, "TIPO_LÂMPADA_DA");
            pm.municipio = extractTableValue(html, "MUNICIPIO");
            pm.nomeLogradouro = extractTableValue(html, "NOME_LOGRADOURO");
            pm.nomeBairro = extractTableValue(html, "NOME_BAIRRO");
            pm.nomeMunicipio = extractTableValue(html, "NOME_MUNICIPIO");
            pm.potenciaLampada = extractTableValue(html, "POTÊNCIA DA LÂMPADA SUBSTITUÍDA");
            pm.tipoLampadaInstalada = extractTableValue(html, "TIPO DE LÂMPADA INSTALADA");
        } catch (Exception e) {}
    }

    private String extractTableValue(String html, String fieldName) {
        try {
            String pattern = "<td[^>]*>" + fieldName + "</td>\\s*<td[^>]*>([^<]*)</td>";
            Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(html);
            if (m.find()) {
                String val = m.group(1).trim();
                return (val.isEmpty() || "null".equalsIgnoreCase(val)) ? null : val;
            }
        } catch (Exception e) {}
        return null;
    }

    private Map<String, Object> buildLightPointDTO(KmlPlacemark pm) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("code", pm.idLampada != null ? pm.idLampada : pm.name);
        dto.put("address", buildAddress(pm));
        dto.put("reference", pm.nomeLogradouro);
        dto.put("district", pm.nomeBairro != null ? pm.nomeBairro : pm.municipio);
        dto.put("city", pm.nomeMunicipio != null ? pm.nomeMunicipio : pm.municipio);
        dto.put("wgsLat", pm.lat);
        dto.put("wgsLon", pm.lon);
        dto.put("srid", 4326);
        Map<String, Object> component = new HashMap<>();
        component.put("type", "LAMPADA");
        String tipo = pm.tipoLampadaInstalada != null ? pm.tipoLampadaInstalada : pm.tipoLampada;
        component.put("model", tipo);
        component.put("lampManufacturer", extractManufacturer(tipo));
        component.put("lampType", extractManufacturer(tipo));
        Integer power = parsePower(pm.potenciaLampada);
        if (power != null) component.put("lampPower", power);
        component.put("status", "FUNCIONAL");
        dto.put("components", List.of(component));
        return dto;
    }

    private String buildAddress(KmlPlacemark pm) {
        StringBuilder sb = new StringBuilder();
        if (pm.nomeLogradouro != null) sb.append(pm.nomeLogradouro);
        if (pm.nomeBairro != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(pm.nomeBairro);
        }
        return sb.length() > 0 ? sb.toString() : "Dirección no especificada";
    }

    private void sendBatchToRabbitMQ(List<Map<String, Object>> batch) {
        try { rabbitTemplate.convertAndSend(LIGHTPOINT_EXCHANGE, LIGHTPOINT_IMPORT_KEY, batch); }
        catch (Exception e) { throw new RuntimeException("Error enviando batch", e); }
    }

    private void completeJob(ImportJob job, int processed) {
        job.setStatus("COMPLETED");
        job.setRowsProcessed(processed);
        job.setCompletedAt(LocalDateTime.now());
        jobRepository.save(job);
    }

    private void handleImportError(ImportJob job, Exception e) {
        job.setStatus("FAILED");
        job.setErrorMessage(e.getMessage());
        job.setCompletedAt(LocalDateTime.now());
        jobRepository.save(job);
    }

    private String getXmlTagValue(String tag, Element element) {
        NodeList nodeList = element.getElementsByTagName(tag);
        if (nodeList.getLength() > 0) {
            Node node = nodeList.item(0);
            if (node != null && node.getFirstChild() != null) return node.getFirstChild().getNodeValue();
        }
        return null;
    }

    private Geometry ensureSRID(Geometry geom, int expectedSRID) {
        try { geom.setSRID(expectedSRID); return geom; } catch (Exception e) { return geom; }
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
        for (org.geotools.api.feature.type.PropertyDescriptor pd : f.getType().getDescriptors()) {
            String key = pd.getName().toString();
            if ("the_geom".equals(key)) continue;
            Object val = f.getAttribute(key);
            if (val != null) props.put(key, val);
        }
        try { return objectMapper.writeValueAsString(props); } catch (Exception e) { return "{}"; }
    }

    private Map<String, Object> mapAttributes(SimpleFeature f, String mappingJson) {
        Map<String, Object> dto = new HashMap<>();
        try {
            if (mappingJson != null && !mappingJson.isBlank()) {
                Map<String, String> mapping = objectMapper.readValue(mappingJson, Map.class);
                for (var entry : mapping.entrySet()) {
                    Object value = f.getAttribute(entry.getKey());
                    if (value != null) dto.put(entry.getValue(), value);
                }
            } else {
                dto.put("code", f.getID());
            }
        } catch (Exception e) { /* ignore */ }
        return dto;
    }

    private static class KmlPlacemark {
        String name;
        double lon; double lat;
        String idLampada; String tipoLampada; String municipio;
        Integer qtdLampadas; String nomeLogradouro; String nomeBairro; String nomeMunicipio;
        String potenciaLampada; String tipoLampadaInstalada;
        KmlPlacemark(String name, double lon, double lat) {
            this.name = name; this.lon = lon; this.lat = lat; this.qtdLampadas = 1;
        }
    }
}