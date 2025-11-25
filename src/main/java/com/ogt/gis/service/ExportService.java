package com.ogt.gis.service;

import com.ogt.gis.entity.ExportJob;
import com.ogt.gis.repository.ExportJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportService {

    private final ExportJobRepository jobRepository;
    private final RabbitTemplate rabbitTemplate;

    // Ajustar si querés otro exchange/routing
    private static final String EXCHANGE = "ogt.gis.events";
    private static final String ROUTING = "gis.export.queue";

    public UUID queueExport(String layerCode, String format, String filtersJson) {
        ExportJob job = ExportJob.builder()
                .jobType("EXPORT_" + format.toUpperCase())
                .status("PENDING")
                .parameters("layer:" + layerCode + (filtersJson != null ? ";filters:" + filtersJson : ""))
                .createdAt(LocalDateTime.now())
                .build();

        job = jobRepository.save(job);

        String message = job.getId() + ";" + layerCode + ";" + format.toUpperCase();
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING, message);

        log.info("🚀 Export encolado: {} format={} layer={}", job.getId(), format, layerCode);

        return job.getId();
    }

    public ExportJob getStatus(String jobId) {
        return jobRepository.findById(UUID.fromString(jobId))
                .orElseThrow(() -> new RuntimeException("ExportJob not found: " + jobId));
    }
}
