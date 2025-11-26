package com.ogt.gis.controller;

import com.ogt.gis.service.GridService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/gis/grids")
@RequiredArgsConstructor
public class GridController {

    private final GridService gridService;

    @PostMapping("/generate")
    public ResponseEntity<String> generate(
            @RequestParam String scale,
            @RequestParam double x,
            @RequestParam double y,
            @RequestParam double w,
            @RequestParam double h,
            @RequestParam int rows,
            @RequestParam int cols,
            @RequestParam(required = false) Integer srid // ✅ NUEVO (opcional)
    ) {
        gridService.generateGrid(scale, x, y, w, h, rows, cols, srid);
        return ResponseEntity.ok("Cuadrícula generada exitosamente en EPSG:" +
                (srid != null ? srid : "auto-detectado"));
    }

    /**
     * ✅ NUEVO - Recalcula el conteo de puntos de luz por celda.
     * <p>
     * Este endpoint es pesado y debería ejecutarse:
     * 1. Después de una importación masiva de postes.
     * 2. Diariamente mediante un scheduler.
     * <p>
     * Ejemplo: POST /api/gis/grids/recalculate-counts
     */
    @PostMapping("/recalculate-counts")
    public ResponseEntity<Map<String, Object>> recalculateCounts() {
        int updated = gridService.recalculateLightPointCounts();

        return ResponseEntity.ok(Map.of(
                "message", "Conteo de puntos recalculado exitosamente",
                "cellsUpdated", updated,
                "timestamp", java.time.LocalDateTime.now()
        ));
    }
}