package com.ogt.gis.service;

import com.ogt.gis.entity.StandardGridCell;
import com.ogt.gis.repository.StandardGridCellRepository;
import com.ogt.gis.util.GeoJSONHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GridGeoJsonService {

    private final StandardGridCellRepository repository;

    /**
     * Devuelve todas las cuadrículas de una escala en formato GeoJSON FeatureCollection.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getGridsAsGeoJson(String scale) {
        List<StandardGridCell> grids;

        if (scale == null || scale.isBlank() || scale.equalsIgnoreCase("all")) {
            grids = repository.findAll();
        } else {
            grids = repository.findByScale(scale);
        }

        Map<String, Object> featureCollection = new HashMap<>();
        featureCollection.put("type", "FeatureCollection");

        List<Map<String, Object>> features = grids.stream()
                .filter(g -> g.getGeom() != null)
                .map(this::gridToGeoJsonFeature)
                .collect(Collectors.toList());

        featureCollection.put("features", features);

        return featureCollection;
    }

    /**
     * Obtiene las escalas disponibles.
     */
    @Transactional(readOnly = true)
    public List<String> getAvailableScales() {
        return repository.findDistinctScales();
    }

    /**
     * Convierte un StandardGridCell a un GeoJSON Feature.
     */
    private Map<String, Object> gridToGeoJsonFeature(StandardGridCell grid) {
        Map<String, Object> feature = new HashMap<>();
        feature.put("type", "Feature");

        // Geometría
        try {
            feature.put("geometry", GeoJSONHelper.geometryToGeoJson(grid.getGeom()));
        } catch (Exception e) {
            feature.put("geometry", null);
        }

        // Propiedades
        Map<String, Object> properties = new HashMap<>();
        properties.put("id", grid.getId() != null ? grid.getId().toString() : null);
        properties.put("gridCode", grid.getGridCode());
        properties.put("scale", grid.getScale());
        properties.put("lightPointsCount", grid.getLightPointsCount());
        feature.put("properties", properties);

        return feature;
    }
}