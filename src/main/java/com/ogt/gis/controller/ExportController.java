package com.ogt.gis.controller;

import com.ogt.common.audit.Audit;
import com.ogt.gis.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/gis/export")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'TECHNICIAN', 'FISCAL')")
public class ExportController {

    private final ExportService exportService;

    @PostMapping
    @Audit(action = "EXPORTAR_CAMADA", module = "GIS", resourceType = "ExportJob", captureParams = true)
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