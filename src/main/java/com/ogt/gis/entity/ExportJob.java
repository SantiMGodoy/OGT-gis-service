package com.ogt.gis.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "export_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExportJob {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "job_type", nullable = false)
    private String jobType; // EXPORT

    @Column(nullable = false)
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String parameters; // JSON con parámetros (format, filters...)

    @Column(name = "file_url", length = 1000)
    private String fileUrl;

    @Column(name = "rows_exported")
    private Integer rowsExported;

    @Column(name = "error_message", columnDefinition = "NVARCHAR(MAX)")
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
