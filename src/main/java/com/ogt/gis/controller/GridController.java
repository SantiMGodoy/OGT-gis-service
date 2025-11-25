package com.ogt.gis.controller;

import com.ogt.gis.service.GridService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/gis/grids")
@RequiredArgsConstructor
public class GridController {

    private final GridService gridService;

    @PostMapping("/generate")
    public ResponseEntity<String> generate(
            @RequestParam String scale,
            @RequestParam double x, @RequestParam double y,
            @RequestParam double w, @RequestParam double h,
            @RequestParam int rows, @RequestParam int cols
    ) {
        gridService.generateGrid(scale, x, y, w, h, rows, cols);
        return ResponseEntity.ok("Cuadrícula generada exitosamente.");
    }
}