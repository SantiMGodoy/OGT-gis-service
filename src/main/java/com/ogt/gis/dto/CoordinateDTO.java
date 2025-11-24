package com.ogt.gis.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoordinateDTO {
    @NotNull(message = "La latitud/X es obligatoria")
    private Double x; // Puede ser Longitud (WGS84) o Este (UTM)

    @NotNull(message = "La longitud/Y es obligatoria")
    private Double y; // Puede ser Latitud (WGS84) o Norte (UTM)

    @NotNull(message = "El SRID de origen es obligatorio")
    private Integer srid; // Ej: 4326 (GPS) o 31984 (SIRGAS)
}