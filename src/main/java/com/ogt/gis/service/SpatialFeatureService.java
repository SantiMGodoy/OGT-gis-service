package com.ogt.gis.service;

import com.ogt.gis.dto.SpatialFeatureDTO;
import com.ogt.gis.entity.SpatialFeature;
import com.ogt.gis.repository.SpatialFeatureRepository;
import com.ogt.gis.util.GeoJSONHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SpatialFeatureService {

    private final SpatialFeatureRepository featureRepository;

    @Transactional(readOnly = true)
    public Page<SpatialFeatureDTO> getFeaturesByLayer(String layerCode, Pageable pageable) {
        return featureRepository.findByLayerCode(layerCode, pageable)
                .map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public SpatialFeatureDTO getById(UUID id) {
        SpatialFeature sf = featureRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Feature no encontrada: " + id));
        return toDTO(sf);
    }

    // La creación es exclusiva del GisImportWorker.

    private SpatialFeatureDTO toDTO(SpatialFeature entity) {
        try {
            return SpatialFeatureDTO.builder()
                    .id(entity.getId())
                    .layerCode(entity.getLayer() != null ? entity.getLayer().getCode() : null)
                    .externalId(entity.getExternalId())
                    .geometry(GeoJSONHelper.geometryToGeoJson(entity.getGeom()))
                    .properties(GeoJSONHelper.parseProperties(entity.getProperties()))
                    .createdAt(entity.getCreatedAt())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Error mapeando DTO", e);
        }
    }
}