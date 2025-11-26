package com.ogt.gis.service;

import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.locationtech.jts.operation.valid.TopologyValidationError;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class GeometryValidationService {

    private final WKTReader wktReader = new WKTReader();

    /**
     * Valida una geometría WKT y proporciona diagnóstico detallado.
     *
     * @param wkt Texto Well-Known Text (ej: "POINT(-40.3 -20.3)")
     * @return Mapa con información de validez, tipo, vértices y diagnóstico de errores
     */
    public Map<String, Object> validateWKT(String wkt) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 1. Parsear texto a geometría
            Geometry geom = wktReader.read(wkt);

            // 2. Información básica
            result.put("geometryType", geom.getGeometryType());
            result.put("vertexCount", geom.getNumPoints());
            result.put("srid", geom.getSRID());
            result.put("dimension", geom.getDimension());

            // 3. Verificar validez topológica con IsValidOp (JTS avanzado)
            IsValidOp validOp = new IsValidOp(geom);
            boolean isValid = validOp.isValid();
            result.put("valid", isValid);

            if (!isValid) {
                // 4. Diagnóstico detallado del error topológico
                TopologyValidationError error = validOp.getValidationError();

                result.put("errorType", getErrorTypeName(error.getErrorType()));
                result.put("errorMessage", error.getMessage());

                if (error.getCoordinate() != null) {
                    result.put("errorLocation", Map.of(
                            "x", error.getCoordinate().x,
                            "y", error.getCoordinate().y
                    ));
                }

                // 5. Intentar reparación automática (buffer 0 trick)
                try {
                    Geometry fixed = geom.buffer(0);

                    if (fixed.isValid()) {
                        result.put("canBeFixed", true);
                        result.put("fixedWKT", fixed.toText());
                        result.put("fixMethod", "BUFFER_ZERO");
                        log.info("✅ Geometría reparada automáticamente usando buffer(0)");
                    } else {
                        result.put("canBeFixed", false);
                        result.put("fixMessage", "La reparación buffer(0) no resolvió el problema");
                    }
                } catch (Exception ex) {
                    result.put("canBeFixed", false);
                    result.put("fixError", ex.getMessage());
                    log.warn("⚠️ No se pudo reparar la geometría: {}", ex.getMessage());
                }

            } else {
                // Geometría válida
                result.put("message", "La geometría es topológicamente válida");
            }

        } catch (Exception e) {
            // Error de parseo (WKT malformado)
            result.put("valid", false);
            result.put("errorType", "PARSE_ERROR");
            result.put("errorMessage", "Error al parsear WKT: " + e.getMessage());
            log.error("❌ Error parseando WKT: {}", wkt, e);
        }

        return result;
    }

    /**
     * Convierte el código numérico de error de JTS a un nombre legible.
     */
    private String getErrorTypeName(int errorType) {
        return switch (errorType) {
            case TopologyValidationError.ERROR -> "GENERIC_ERROR";
            case TopologyValidationError.REPEATED_POINT -> "REPEATED_POINT";
            case TopologyValidationError.HOLE_OUTSIDE_SHELL -> "HOLE_OUTSIDE_SHELL";
            case TopologyValidationError.NESTED_HOLES -> "NESTED_HOLES";
            case TopologyValidationError.DISCONNECTED_INTERIOR -> "DISCONNECTED_INTERIOR";
            case TopologyValidationError.SELF_INTERSECTION -> "SELF_INTERSECTION";
            case TopologyValidationError.RING_SELF_INTERSECTION -> "RING_SELF_INTERSECTION";
            case TopologyValidationError.NESTED_SHELLS -> "NESTED_SHELLS";
            case TopologyValidationError.DUPLICATE_RINGS -> "DUPLICATE_RINGS";
            case TopologyValidationError.TOO_FEW_POINTS -> "TOO_FEW_POINTS";
            case TopologyValidationError.INVALID_COORDINATE -> "INVALID_COORDINATE";
            case TopologyValidationError.RING_NOT_CLOSED -> "RING_NOT_CLOSED";
            default -> "UNKNOWN_ERROR_" + errorType;
        };
    }
}