package com.ogt.gis.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "import_jobs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportJob {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "job_type", nullable = false)
    private String jobType; // IMPORT

    @Column(nullable = false)
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String parameters; // Ej: "SRID:31984"

    @Column(name = "file_url", length = 1000)
    private String fileUrl;

    @Column(name = "rows_processed")
    private Integer rowsProcessed;

    @Column(name = "error_message", columnDefinition = "NVARCHAR(MAX)")
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "total_rows")
    private Integer totalRows;

    @Column(name = "rows_with_errors")
    private Integer rowsWithErrors;

    @Column(name = "progress_percentage")
    private Integer progressPercentage;

    @Column(name = "error_summary", columnDefinition = "NVARCHAR(MAX)")
    private String errorSummary;

    @Column(name = "processing_speed") // filas por segundo
    private Double processingSpeed;

    public void updateProgress(int processed, int total, int errors) {
        this.rowsProcessed = processed;
        this.totalRows = total;
        this.rowsWithErrors = errors;
        this.progressPercentage = total > 0 ? (int) ((processed * 100.0) / total) : 0;
    }

    public void calculateSpeed() {
        if (startedAt != null && rowsProcessed != null && rowsProcessed > 0) {
            long elapsedSeconds = Duration.between(startedAt, LocalDateTime.now()).getSeconds();
            if (elapsedSeconds > 0) {
                this.processingSpeed = (double) rowsProcessed / elapsedSeconds;
            }
        }
    }
}
