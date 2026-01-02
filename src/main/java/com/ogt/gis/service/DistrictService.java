package com.ogt.gis.service;

import com.ogt.gis.entity.DistrictBoundary;
import com.ogt.gis.repository.DistrictBoundaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ogt.gis.util.GeoJSONHelper;

import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DistrictService {

    private final DistrictBoundaryRepository repository;

    @Transactional(readOnly = true)
    public List<DistrictBoundary> getAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public DistrictBoundary getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Distrito no encontrado: " + id));
    }

    /**
     * Devuelve todos los distritos en formato GeoJSON FeatureCollection.
     * Compatible directamente con Leaflet.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAllAsGeoJson() {
        List<DistrictBoundary> districts = repository.findAll();

        Map<String, Object> featureCollection = new HashMap<>();
        featureCollection.put("type", "FeatureCollection");

        List<Map<String, Object>> features = districts.stream()
                .filter(d -> d.getGeom() != null) // Solo distritos con geometría
                .map(this::districtToGeoJsonFeature)
                .collect(Collectors.toList());

        featureCollection.put("features", features);

        return featureCollection;
    }

    /**
     * Convierte un DistrictBoundary a un GeoJSON Feature.
     */
    private Map<String, Object> districtToGeoJsonFeature(DistrictBoundary district) {
        Map<String, Object> feature = new HashMap<>();
        feature.put("type", "Feature");

        // Geometría
        try {
            feature.put("geometry", GeoJSONHelper.geometryToGeoJson(district.getGeom()));
        } catch (Exception e) {
            feature.put("geometry", null);
        }

        // Propiedades
        Map<String, Object> properties = new HashMap<>();
        properties.put("id", district.getId() != null ? district.getId().toString() : null);
        properties.put("code", district.getCode());
        properties.put("name", district.getName());
        properties.put("area", district.getArea());
        feature.put("properties", properties);

        return feature;
    }
}