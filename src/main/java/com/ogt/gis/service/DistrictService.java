package com.ogt.gis.service;

import com.ogt.gis.entity.DistrictBoundary;
import com.ogt.gis.repository.DistrictBoundaryRepository;
import com.ogt.gis.util.GeoJSONHelper;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DistrictService {

    private final DistrictBoundaryRepository repository;

    @Transactional
    public DistrictBoundary createDistrict(String name, String code, Map<String, Object> geoJson) {
        try {
            // Convertimos el GeoJSON que viene del frontend a Geometría JTS
            Geometry geom = GeoJSONHelper.geoJsonToGeometry(geoJson);
            // Asignamos el sistema de coordenadas local (SIRGAS 2000)
            if (geom != null) geom.setSRID(31984);

            DistrictBoundary district = DistrictBoundary.builder()
                    .name(name)
                    .code(code)
                    .geom(geom)
                    .area(geom != null ? geom.getArea() : 0.0)
                    .build();

            return repository.save(district);
        } catch (Exception e) {
            throw new RuntimeException("Error creando distrito: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<DistrictBoundary> getAll() {
        return repository.findAll();
    }
}