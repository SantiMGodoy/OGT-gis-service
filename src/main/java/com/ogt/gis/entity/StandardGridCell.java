package com.ogt.gis.entity;

import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Geometry;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "standard_grids", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"scale", "grid_code"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StandardGridCell {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 20)
    private String scale; // Ej: "1:5000", "1:10000"

    @Column(name = "grid_code", nullable = false, length = 10)
    private String gridCode; // Ej: "A1", "B2"

    @Column(columnDefinition = "geometry", nullable = false)
    private Geometry geom; // El cuadrado geográfico

    @Column(name = "light_points_count")
    private Integer lightPointsCount = 0;

    private LocalDateTime lastUpdated;
}