package com.ogt.gis.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;
import org.locationtech.jts.geom.Geometry;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "spatial_features")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpatialFeature {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "layer_id")
    private MapLayer layer;

    @Column(name = "external_id")
    private String externalId;

    @Column(columnDefinition = "geometry")
    private Geometry geom;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String properties;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
