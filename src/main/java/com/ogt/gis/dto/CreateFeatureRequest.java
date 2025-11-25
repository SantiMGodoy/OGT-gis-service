package com.ogt.gis.dto;

import lombok.Data;
import java.util.Map;

@Data
public class CreateFeatureRequest {
    private Map<String, Object> geometry;
    private Map<String, Object> properties;
    private String externalId;
}
