package com.ogt.gis.controller;

import com.ogt.gis.dto.SpatialFeatureDTO;
import com.ogt.gis.service.SpatialFeatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/gis/features")
@RequiredArgsConstructor
public class SpatialFeatureController {

    private final SpatialFeatureService featureService;

    // GET /api/gis/features/{layerCode}
    // Permite al frontend descargar una capa (ej. "RIOS", "ZONAS") para pintarla.
    @GetMapping("/{layerCode}")
    public ResponseEntity<Page<SpatialFeatureDTO>> getFeatures(
            @PathVariable String layerCode,
            Pageable pageable
    ) {
        return ResponseEntity.ok(featureService.getFeaturesByLayer(layerCode, pageable));
    }

    // GET /api/gis/features/id/{id}
    @GetMapping("/id/{id}")
    public ResponseEntity<SpatialFeatureDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(featureService.getById(id));
    }
}