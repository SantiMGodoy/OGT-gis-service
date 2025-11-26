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
}