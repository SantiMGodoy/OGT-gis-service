package com.ogt.gis.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class SpatialFeatureDTO {
    private UUID id;
    private String layerCode;
    private String externalId;
    private Map<String, Object> geometry; // GeoJSON
    private Map<String, Object> properties;
    private LocalDateTime createdAt;
}