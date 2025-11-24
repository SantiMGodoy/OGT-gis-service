package com.ogt.gis.controller;

import com.ogt.gis.dto.CoordinateDTO;
import com.ogt.gis.dto.FeatureResponseDTO;
import com.ogt.gis.service.CoordinateService;
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
    private final com.ogt.gis.service.GeometryValidationService validationService;

    // Endpoint: POST /api/gis/convert
    // Convierte una coordenada al SRID solicitado (query param ?targetSrid=...)
    @PostMapping("/convert")
    public ResponseEntity<CoordinateDTO> convertCoordinate(
            @RequestBody @Valid CoordinateDTO source,
            @RequestParam(defaultValue = "4326") Integer targetSrid // Por defecto convierte a GPS (WGS84)
    ) {
        return ResponseEntity.ok(coordinateService.convert(source, targetSrid));
    }

    // Endpoint: GET /api/gis/nearest
    // Ejemplo: /api/gis/nearest?lat=-20.3155&lon=-40.3128&layer=LIGHT_POINTS&limit=5
    @GetMapping("/nearest")
    public ResponseEntity<List<FeatureResponseDTO>> findNearest(
            @RequestParam Double lat,
            @RequestParam Double lon,
            @RequestParam(defaultValue = "LIGHT_POINTS") String layer,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return ResponseEntity.ok(spatialQueryService.findNearest(lat, lon, layer, limit));
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validateGeometry(@RequestBody String wkt) {
        return ResponseEntity.ok(validationService.validateWKT(wkt));
    }
}