package com.ogt.gis.dto;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class FeatureResponseDTO {
    private UUID id;
    private String externalId; // El ID del poste (LP-001)
    private String properties; // JSON con datos extra
    private Double distanceMeters; // A qué distancia estaba del punto de búsqueda
    private CoordinateDTO location; // Ubicación del objeto
}