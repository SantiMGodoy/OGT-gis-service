package com.ogt.gis.service;

import com.ogt.common.exception.BusinessException;
import com.ogt.common.exception.ResourceNotFoundException;
import com.ogt.gis.dto.MapLayerDTO;
import com.ogt.gis.entity.MapLayer;
import com.ogt.gis.repository.MapLayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MapLayerService {

    private final MapLayerRepository layerRepository;

    @Transactional(readOnly = true)
    public List<MapLayerDTO> getAllLayers() {
        return layerRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public MapLayerDTO createLayer(MapLayerDTO dto) {
        if (layerRepository.existsByCode(dto.getCode())) {
            throw new BusinessException("Ya existe una capa con el código: " + dto.getCode());
        }
        MapLayer layer = toEntity(dto);
        return toDTO(layerRepository.save(layer));
    }

    @Transactional
    public MapLayerDTO updateLayer(UUID id, MapLayerDTO dto) {
        MapLayer layer = layerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Capa no encontrada"));

        layer.setName(dto.getName());
        layer.setStyle(dto.getStyle());
        layer.setIsActive(dto.getIsActive());
        layer.setZIndex(dto.getZIndex());

        return toDTO(layerRepository.save(layer));
    }

    // Mappers simples
    private MapLayerDTO toDTO(MapLayer entity) {
        return MapLayerDTO.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .type(entity.getType())
                .source(entity.getSource())
                .style(entity.getStyle())
                .isActive(entity.getIsActive())
                .zIndex(entity.getZIndex())
                .build();
    }

    private MapLayer toEntity(MapLayerDTO dto) {
        return MapLayer.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .type(dto.getType())
                .source(dto.getSource())
                .style(dto.getStyle())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .zIndex(dto.getZIndex() != null ? dto.getZIndex() : 0)
                .build();
    }
}