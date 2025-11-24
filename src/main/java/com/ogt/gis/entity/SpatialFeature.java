package com.ogt.gis.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.locationtech.jts.geom.Geometry;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "spatial_features")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SpatialFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "layer_id")
    private MapLayer layer; // Vincula con una capa lógica (ej. "POSTES")

    @Column(name = "external_id", length = 200)
    private String externalId; // ID del objeto en otro servicio (ej. ID del LightPoint)

    // Esta es la magia de Hibernate Spatial
    // SQL Server lo guardará como tipo 'geometry'
    @Column(columnDefinition = "geometry")
    private Geometry geom;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String properties; // JSON con atributos extra

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}