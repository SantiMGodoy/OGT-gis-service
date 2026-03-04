package com.ogt.gis.controller;

import com.ogt.common.audit.Audit;
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

    /**
     * Generates a cartographic grid.
     * Origin (x, y) should be in WGS84 (longitude, latitude).
     * Width/height in meters. Grid is generated in UTM and stored as WGS84.
     */
    @PostMapping("/generate")
    @Audit(action = "GERAR_GRADE", module = "GIS", resourceType = "Grid", captureParams = true)
    public ResponseEntity<Map<String, Object>> generate(
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
        int totalCells = rows * cols;
        return ResponseEntity.ok(Map.of(
                "message", "Grid generated successfully",
                "scale", scale,
                "totalCells", totalCells,
                "srid", srid != null ? srid : "auto-detected"
        ));
    }

    /**
     * Returns grids as GeoJSON FeatureCollection.
     * If no scale specified, returns all.
     */
    @GetMapping("/geojson")
    public ResponseEntity<Map<String, Object>> getGridsGeoJson(
            @RequestParam(required = false, defaultValue = "all") String scale
    ) {
        return ResponseEntity.ok(gridGeoJsonService.getGridsAsGeoJson(scale));
    }

    /**
     * Returns available grid scales.
     */
    @GetMapping("/scales")
    public ResponseEntity<List<String>> getAvailableScales() {
        return ResponseEntity.ok(gridGeoJsonService.getAvailableScales());
    }

    /**
     * Recalculates light point counts for all grid cells.
     */
    @PostMapping("/recalculate-counts")
    @Audit(action = "RECALCULAR_CONTEO_GRADE", module = "GIS", resourceType = "Grid")
    public ResponseEntity<Map<String, Object>> recalculateCounts() {
        int updated = gridService.recalculateLightPointCounts();
        return ResponseEntity.ok(Map.of(
                "message", "Recalculation complete",
                "cellsUpdated", updated
        ));
    }

    /**
     * Deletes all grid cells for a given scale.
     */
    @DeleteMapping
    @Audit(action = "DELETAR_GRADE", module = "GIS", resourceType = "Grid", captureParams = true)
    public ResponseEntity<Map<String, Object>> deleteByScale(
            @RequestParam String scale
    ) {
        int deleted = gridService.deleteGridsByScale(scale);
        return ResponseEntity.ok(Map.of(
                "message", "Grid deleted",
                "scale", scale,
                "cellsDeleted", deleted
        ));
    }
}
