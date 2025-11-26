package com.ogt.gis.controller;

import com.ogt.gis.entity.ExportJob;
import com.ogt.gis.entity.ImportJob;
import com.ogt.gis.repository.ExportJobRepository;
import com.ogt.gis.repository.ImportJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/gis/jobs")
@RequiredArgsConstructor
public class JobController {

    private final ImportJobRepository importJobRepository;
    private final ExportJobRepository exportJobRepository;

    // Import Jobs
    @GetMapping("/import")
    public ResponseEntity<List<ImportJob>> getAllImportJobs() {
        return ResponseEntity.ok(importJobRepository.findAll());
    }

    @GetMapping("/import/{id}")
    public ResponseEntity<ImportJob> getImportJob(@PathVariable UUID id) {
        return ResponseEntity.of(importJobRepository.findById(id));
    }

    // Export Jobs
    @GetMapping("/export")
    public ResponseEntity<List<ExportJob>> getAllExportJobs() {
        return ResponseEntity.ok(exportJobRepository.findAll());
    }

    @GetMapping("/export/{id}")
    public ResponseEntity<ExportJob> getExportJob(@PathVariable UUID id) {
        return ResponseEntity.of(exportJobRepository.findById(id));
    }
}