package com.ogt.gis.validation;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CoordinateValidation {
    private boolean valid;
    private Double latitude;
    private Double longitude;
    private String errorMessage;
    private CoordinateValidationType type;

    public enum CoordinateValidationType {
        VALID,
        INVALID,
        SWAPPED,
        OUT_OF_RANGE
    }

    public static CoordinateValidation valid(Double lat, Double lon) {
        return CoordinateValidation.builder()
                .valid(true)
                .latitude(lat)
                .longitude(lon)
                .type(CoordinateValidationType.VALID)
                .build();
    }

    public static CoordinateValidation invalid(String message) {
        return CoordinateValidation.builder()
                .valid(false)
                .errorMessage(message)
                .type(CoordinateValidationType.INVALID)
                .build();
    }

    public static CoordinateValidation swapped(Double lat, Double lon) {
        return CoordinateValidation.builder()
                .valid(true)
                .latitude(lat)
                .longitude(lon)
                .type(CoordinateValidationType.SWAPPED)
                .errorMessage("Coordenadas invertidas - auto-corregidas")
                .build();
    }

    public static CoordinateValidation outOfRange(Double lat, Double lon, String message) {
        return CoordinateValidation.builder()
                .valid(false)
                .latitude(lat)
                .longitude(lon)
                .errorMessage(message)
                .type(CoordinateValidationType.OUT_OF_RANGE)
                .build();
    }
}