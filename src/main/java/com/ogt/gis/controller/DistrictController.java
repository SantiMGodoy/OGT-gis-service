package com.ogt.gis.controller;

import com.ogt.gis.entity.DistrictBoundary;
import com.ogt.gis.service.DistrictService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gis/districts")
@RequiredArgsConstructor
public class DistrictController {

    private final DistrictService districtService;

    @GetMapping
    public ResponseEntity<List<DistrictBoundary>> getAll() {
        return ResponseEntity.ok(districtService.getAll());
    }

    @PostMapping
    public ResponseEntity<DistrictBoundary> create(
            @RequestParam String name,
            @RequestParam String code,
            @RequestBody Map<String, Object> geoJson // El polígono del barrio
    ) {
        return ResponseEntity.ok(districtService.createDistrict(name, code, geoJson));
    }
}