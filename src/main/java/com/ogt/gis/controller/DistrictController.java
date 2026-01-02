package com.ogt.gis.controller;

import com.ogt.gis.entity.DistrictBoundary;
import com.ogt.gis.service.DistrictService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/gis/districts")
@RequiredArgsConstructor
public class DistrictController {

    private final DistrictService districtService;

    @GetMapping
    public ResponseEntity<List<DistrictBoundary>> getAll() {
        return ResponseEntity.ok(districtService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DistrictBoundary> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(districtService.getById(id));
    }

    /**
     * Devuelve todos los distritos en formato GeoJSON FeatureCollection.
     * Compatible directamente con Leaflet para renderizar polígonos.
     */
    @GetMapping("/geojson")
    public ResponseEntity<Map<String, Object>> getAllAsGeoJson() {
        return ResponseEntity.ok(districtService.getAllAsGeoJson());
    }
}