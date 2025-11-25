package com.ogt.gis.controller;

import com.ogt.gis.entity.ImportJob; // Asegúrate de tener esta entidad o una genérica Job
import com.ogt.gis.entity.ExportJob;
import com.ogt.gis.repository.ImportJobRepository;
import com.ogt.gis.repository.ExportJobRepository;
import com.ogt.gis.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.Map;

@RestController
@RequestMapping("/api/gis") // Ruta base general
@RequiredArgsConstructor
public class JobController {

    private final ImportJobRepository importJobRepository;
    private final ExportJobRepository exportJobRepository;
    private final ExportService exportService;

    // --- GESTIÓN DE JOBS (Admin) ---

    // GET /api/gis/jobs/import -> Listar importaciones
    @GetMapping("/jobs/import")
    public ResponseEntity<List<ImportJob>> getAllImportJobs() {
        return ResponseEntity.ok(importJobRepository.findAll());
    }

    // GET /api/gis/jobs/export -> Listar exportaciones
    @GetMapping("/jobs/export")
    public ResponseEntity<List<ExportJob>> getAllExportJobs() {
        return ResponseEntity.ok(exportJobRepository.findAll());
    }

    // GET /api/gis/jobs/export/{id} -> Ver estado de una exportación
    @GetMapping("/jobs/export/{id}")
    public ResponseEntity<ExportJob> getExportJob(@PathVariable UUID id) {
        return ResponseEntity.of(exportJobRepository.findById(id));
    }

    // --- ACCIONES DE EXPORTACIÓN ---

    /**
     * Endpoint: POST /api/gis/export
     * Inicia un proceso de exportación asíncrono.
     */
    @PostMapping("/export")
    public ResponseEntity<?> exportLayer(
            @RequestParam String layerCode,
            @RequestParam String format, // SHP, DXF, KML
            @RequestParam(required = false) String filters // Opcional
    ) {
        // Llama al servicio que ya tienes
        UUID jobId = exportService.queueExport(layerCode, format, filters);

        return ResponseEntity.accepted().body(Map.of(
                "message", "Exportación iniciada",
                "jobId", jobId
        ));
    }
}