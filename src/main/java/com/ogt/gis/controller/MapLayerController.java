package com.ogt.gis.controller;

import com.ogt.common.audit.Audit;
import com.ogt.gis.dto.MapLayerDTO;
import com.ogt.gis.service.MapLayerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/gis/layers")
@RequiredArgsConstructor
public class MapLayerController {

    private final MapLayerService layerService;

    @GetMapping
    public ResponseEntity<List<MapLayerDTO>> getAllLayers() {
        return ResponseEntity.ok(layerService.getAllLayers());
    }

    @PostMapping
    @Audit(action = "CRIAR_CAMADA", module = "GIS", resourceType = "MapLayer", captureParams = true)
    public ResponseEntity<MapLayerDTO> createLayer(@RequestBody @Valid MapLayerDTO dto) {
        return ResponseEntity.status(201).body(layerService.createLayer(dto));
    }

    @PutMapping("/{id}")
    @Audit(action = "ATUALIZAR_CAMADA", module = "GIS", resourceIdParam = 0, resourceType = "MapLayer")
    public ResponseEntity<MapLayerDTO> updateLayer(@PathVariable UUID id, @RequestBody @Valid MapLayerDTO dto) {
        return ResponseEntity.ok(layerService.updateLayer(id, dto));
    }
}