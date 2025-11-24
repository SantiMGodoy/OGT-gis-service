package com.ogt.gis.controller;

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
    public ResponseEntity<MapLayerDTO> createLayer(@RequestBody @Valid MapLayerDTO dto) {
        return ResponseEntity.status(201).body(layerService.createLayer(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MapLayerDTO> updateLayer(@PathVariable UUID id, @RequestBody @Valid MapLayerDTO dto) {
        return ResponseEntity.ok(layerService.updateLayer(id, dto));
    }
}