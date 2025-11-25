package com.ogt.gis.controller;

import com.ogt.gis.dto.ImportRequestDTO;
import com.ogt.gis.entity.ImportJob;
import com.ogt.gis.service.ImportService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/gis/import")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    @PostMapping
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("layerCode") String layerCode,
            @RequestParam(value = "srid", required = false) Integer srid
    ) {
        UUID jobId = importService.queueImport(file, layerCode, srid);
        return ResponseEntity.ok(jobId);
    }


    @GetMapping("/{jobId}/status")
    public ResponseEntity<?> getStatus(@PathVariable String jobId) {
        return ResponseEntity.ok(importService.getStatus(jobId));
    }
}
