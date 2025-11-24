package com.ogt.gis.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class MapLayerDTO {
    private UUID id;

    @NotBlank(message = "El código es obligatorio (ej. LIGHT_POINTS)")
    private String code;

    @NotBlank(message = "El nombre es obligatorio")
    private String name;

    private String type;    // VECTOR, RASTER
    private String source;  // SHAPEFILE, SYSTEM
    private String style;   // JSON de estilo
    private Boolean isActive;
    private Integer zIndex;
}