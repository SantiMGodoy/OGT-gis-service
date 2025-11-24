package com.ogt.gis.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "map_layers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MapLayer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String code; // Ej: "LIGHT_POINTS"

    @Column(nullable = false)
    private String name; // Ej: "Puntos de Iluminación Pública"

    @Column(length = 30)
    private String type; // VECTOR, RASTER, WMS

    private String source; // Origen de datos (ej. "SHAPEFILE")

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String style; // JSON con reglas de estilo

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Builder.Default
    @Column(name = "z_index")
    private Integer zIndex = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}