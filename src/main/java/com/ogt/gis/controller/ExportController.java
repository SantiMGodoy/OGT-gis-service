package com.ogt.gis.controller;

import com.ogt.gis.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/gis/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    @PostMapping
    public ResponseEntity<?> exportLayer(
            @RequestParam String layerCode,
            @RequestParam String format, // SHP, DXF, KML
            @RequestParam(required = false) String filtersJson
    ) {
        UUID jobId = exportService.queueExport(layerCode, format, filtersJson);
        return ResponseEntity.accepted().body(Map.of(
                "message", "Exportación iniciada",
                "jobId", jobId
        ));
    }
}