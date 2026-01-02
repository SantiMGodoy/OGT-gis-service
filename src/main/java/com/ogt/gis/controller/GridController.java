package com.ogt.gis.controller;

import com.ogt.gis.service.GridGeoJsonService;
import com.ogt.gis.service.GridService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gis/grids")
@RequiredArgsConstructor
public class GridController {

    private final GridService gridService;
    private final GridGeoJsonService gridGeoJsonService;

    @PostMapping("/generate")
    public ResponseEntity<String> generate(
            @RequestParam String scale,
            @RequestParam double x,
            @RequestParam double y,
            @RequestParam double w,
            @RequestParam double h,
            @RequestParam int rows,
            @RequestParam int cols,
            @RequestParam(required = false) Integer srid
    ) {
        gridService.generateGrid(scale, x, y, w, h, rows, cols, srid);
        return ResponseEntity.ok("Cuadrícula generada exitosamente en EPSG:" +
                (srid != null ? srid : "auto-detectado"));
    }

    /**
     * Devuelve las cuadrículas en formato GeoJSON.
     * Si no se especifica escala, devuelve todas.
     */
    @GetMapping("/geojson")
    public ResponseEntity<Map<String, Object>> getGridsGeoJson(
            @RequestParam(required = false, defaultValue = "all") String scale
    ) {
        return ResponseEntity.ok(gridGeoJsonService.getGridsAsGeoJson(scale));
    }

    /**
     * Devuelve las escalas disponibles.
     */
    @GetMapping("/scales")
    public ResponseEntity<List<String>> getAvailableScales() {
        return ResponseEntity.ok(gridGeoJsonService.getAvailableScales());
    }

    // ... resto de métodos existentes (recalculate-counts, etc.)
}