package com.ogt.gis.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "map_layers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MapLayer {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code; // "light_points"

    @Column(nullable = false)
    private String name;

    private String type;

    private String source;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String style;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "z_index")
    private Integer zIndex = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
