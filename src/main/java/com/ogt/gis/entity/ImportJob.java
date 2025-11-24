package com.ogt.gis.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "import_jobs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String jobType; // IMPORT, EXPORT

    @Column(nullable = false, length = 30)
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String parameters;

    private String fileUrl;

    private Integer rowsProcessed;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String errorMessage;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}