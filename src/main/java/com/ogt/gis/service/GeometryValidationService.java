package com.ogt.gis.service;

import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTReader;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class GeometryValidationService {

    private final WKTReader wktReader = new WKTReader();

    public Map<String, Object> validateWKT(String wkt) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 1. Parsear texto a geometría
            Geometry geom = wktReader.read(wkt);

            // 2. Verificar validez topológica (JTS Topology Suite)
            boolean isValid = geom.isValid();
            result.put("valid", isValid);
            result.put("geometryType", geom.getGeometryType());
            result.put("vertexCount", geom.getNumPoints());

            if (!isValid) {
                // Si es inválido, JTS puede decirnos por qué (ej. auto-intersección)
                // En versiones nuevas de JTS esto requiere IsValidOp,
                // por ahora devolvemos simple false.
                result.put("error", "Geometría topológicamente inválida");

                // Intento de reparación simple (buffer 0)
                try {
                    Geometry fixed = geom.buffer(0);
                    result.put("canBeFixed", true);
                    result.put("fixedWKT", fixed.toText());
                } catch (Exception ex) {
                    result.put("canBeFixed", false);
                }
            }

        } catch (Exception e) {
            result.put("valid", false);
            result.put("error", "Error de parseo WKT: " + e.getMessage());
        }
        return result;
    }
}