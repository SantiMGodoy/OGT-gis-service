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
    private String name;

    @Column(length = 30)
    private String type; // VECTOR, RASTER

    // --- NUEVOS CAMPOS DE CONFIGURACIÓN ---

    @Column(name = "geometry_type", length = 50)
    private String geometryType; // POINT, MULTIPOINT, POLYGON, LINESTRING

    @Column(name = "business_target", length = 50)
    private String businessTarget; // NONE, LIGHT_POINT_SERVICE, INVENTORY_SERVICE, DISTRICTS

    @Column(name = "srid")
    private Integer srid; // Ej: 31984 (El sistema esperará/convertirá a esto)

    @Column(name = "attribute_mapping", columnDefinition = "NVARCHAR(MAX)")
    private String attributeMapping; // JSON para mapear columnas del SHP a campos del DTO

    // ---------------------------------------

    private String source;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String style;

    @Builder.Default
    private Boolean isActive = true;

    private Integer zIndex;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}