package com.ogt.gis.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.geotools.geojson.geom.GeometryJSON;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

/**
 * Helpers to convert JTS Geometry <-> GeoJSON (as Map)
 */
public final class GeoJSONHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    // precision not critical here
    private static final GeometryJSON GEOJSON = new GeometryJSON(10);

    private static final GeometryFactory GF = new GeometryFactory();

    private GeoJSONHelper() {}

    /** Convert JTS Geometry -> GeoJSON Map */
    public static Map<String, Object> geometryToGeoJson(Geometry geom) throws Exception {
        if (geom == null) return null;
        StringWriter writer = new StringWriter();
        GEOJSON.write(geom, writer);
        String json = writer.toString();
        return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    /** Convert GeoJSON object (Map or JSON string) -> JTS Geometry */
    @SuppressWarnings("unchecked")
    public static Geometry geoJsonToGeometry(Object geoJsonObj) throws Exception {
        if (geoJsonObj == null) return null;
        String json;
        if (geoJsonObj instanceof String) {
            json = (String) geoJsonObj;
        } else {
            json = MAPPER.writeValueAsString(geoJsonObj);
        }
        StringReader reader = new StringReader(json);
        Geometry geom = GEOJSON.read(reader);
        if (geom != null) geom.setSRID(0); // caller should set SRID appropriately
        return geom;
    }

    /** Parse a properties JSON string to Map */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseProperties(String json) throws Exception {
        if (json == null || json.isBlank()) return null;
        return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    /** Convert properties Map -> JSON String */
    public static String propertiesToJson(Map<String, Object> map) throws Exception {
        if (map == null) return null;
        return MAPPER.writeValueAsString(map);
    }
}
