package com.ogt.gis.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * DTO público para features.
 * - geometry: GeoJSON object (Map) following RFC7946
 * - properties: parsed map of properties
 */
@Data
@Builder
public class SpatialFeatureDTO {
    private UUID id;
    private String layerCode;
    private String externalId;
    private Map<String, Object> geometry;      // GeoJSON geometry object
    private Map<String, Object> properties;    // Parsed properties
    private LocalDateTime createdAt;
}
