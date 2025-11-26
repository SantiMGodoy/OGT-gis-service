package com.ogt.gis.controller;

import com.ogt.gis.dto.CoordinateDTO;
import com.ogt.gis.dto.FeatureResponseDTO;
import com.ogt.gis.service.CoordinateService;
import com.ogt.gis.service.GeometryValidationService;
import com.ogt.gis.service.SpatialQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/gis")
@RequiredArgsConstructor
public class GisController {

    private final CoordinateService coordinateService;
    private final SpatialQueryService spatialQueryService;
    private final GeometryValidationService validationService;

    // 1. Conversión de Coordenadas
    @PostMapping("/convert")
    public ResponseEntity<CoordinateDTO> convertCoordinate(
            @RequestBody @Valid CoordinateDTO source,
            @RequestParam(defaultValue = "4326") Integer targetSrid
    ) {
        return ResponseEntity.ok(coordinateService.convert(source, targetSrid));
    }

    // 2. Validación de Geometría (WKT)
    @PostMapping("/validate")
    public ResponseEntity<?> validateGeometry(@RequestBody String wkt) {
        return ResponseEntity.ok(validationService.validateWKT(wkt));
    }

    // 3. Búsqueda de Cercanía (Específica por capa)
    // GET /api/gis/layers/LIGHT_POINTS/nearest?lat=...&lon=...
    @GetMapping("/layers/{layerCode}/nearest")
    public ResponseEntity<List<FeatureResponseDTO>> findNearest(
            @PathVariable String layerCode,
            @RequestParam Double lat,
            @RequestParam Double lon,
            @RequestParam(defaultValue = "5") int limit
    ) {
        // Nota: Si layerCode es "LIGHT_POINTS", recuerda que GIS no tiene los datos.
        // Este endpoint es útil para capas que SÍ están en GIS (Distritos, Zonas de Riesgo).
        // Para postes, el frontend debería llamar a light-point-service.
        return ResponseEntity.ok(spatialQueryService.findNearest(lat, lon, layerCode, limit));
    }

    /**
     * ✅ NUEVO - Detecta la zona UTM correcta para una longitud dada.
     *
     * Ejemplo: GET /api/gis/utm-zone?lon=-40.3128
     *
     * Respuesta:
     * {
     *   "longitude": -40.3128,
     *   "utmZone": "24S",
     *   "epsgCode": 31984,
     *   "zoneName": "UTM 24S",
     *   "states": ["ES", "RJ", "SP", ...]
     * }
     */
    @GetMapping("/utm-zone")
    public ResponseEntity<Map<String, Object>> getUTMZone(@RequestParam Double lon) {
        Integer srid = coordinateService.detectUTMZone(lon);
        Map<String, Object> zoneInfo = coordinateService.getUTMZoneInfo(srid);

        // Añadir longitud consultada a la respuesta
        java.util.Map<String, Object> response = new java.util.HashMap<>(zoneInfo);
        response.put("longitude", lon);

        return ResponseEntity.ok(response);
    }

    /**
     * ✅ NUEVO - Calcula la distancia entre dos features espaciales.
     *
     * Ejemplo: GET /api/gis/distance?fromId=uuid1&toId=uuid2
     */
    @GetMapping("/distance")
    public ResponseEntity<Map<String, Object>> calculateDistance(
            @RequestParam UUID fromId,
            @RequestParam UUID toId
    ) {
        return ResponseEntity.ok(spatialQueryService.calculateDistance(fromId, toId));
    }

    /**
     * ✅ NUEVO - Encuentra features dentro de un polígono GeoJSON.
     *
     * Ejemplo:
     * POST /api/gis/within?layerCode=DISTRICTS
     * Body: { "type": "Polygon", "coordinates": [[[-40.3, -20.3], ...]] }
     */
    @PostMapping("/within")
    public ResponseEntity<List<FeatureResponseDTO>> findWithin(
            @RequestBody Map<String, Object> geoJsonPolygon,
            @RequestParam String layerCode
    ) {
        return ResponseEntity.ok(spatialQueryService.findWithin(geoJsonPolygon, layerCode));
    }
}