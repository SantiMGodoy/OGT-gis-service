package com.ogt.gis.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.locationtech.jts.geom.Geometry;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "district_boundaries")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DistrictBoundary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(length = 100)
    private String code;

    private String name;

    @Column(columnDefinition = "geometry")
    private Geometry geom; // Polígono del distrito

    private Double area;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String metadata;

    @CreationTimestamp
    private LocalDateTime createdAt;
}