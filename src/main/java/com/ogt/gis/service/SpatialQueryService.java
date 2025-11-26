package com.ogt.gis.service;

import com.ogt.gis.dto.CoordinateDTO;
import com.ogt.gis.dto.FeatureResponseDTO;
import com.ogt.gis.dto.SpatialFeatureDTO;
import com.ogt.gis.entity.DistrictBoundary;
import com.ogt.gis.entity.SpatialFeature;
import com.ogt.gis.repository.DistrictBoundaryRepository;
import com.ogt.gis.repository.SpatialFeatureRepository;
import com.ogt.gis.util.GeoJSONHelper;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SpatialQueryService {

    private final SpatialFeatureRepository featureRepository;
    private final DistrictBoundaryRepository districtRepository; // Para buscar distrito por punto
    private final CoordinateService coordinateService;

    private final GeometryFactory geometryFactory = new GeometryFactory();
    private static final int STORAGE_SRID = 31984;

    /**
     * Busca features genéricos cercanos (NO POSTES).
     * Útil para ver qué "Zonas de Riesgo" o "Ríos" hay cerca.
     */
    @Transactional(readOnly = true)
    public List<FeatureResponseDTO> findNearest(Double lat, Double lon, String layerCode, int limit) {
        // 1. Validación de Arquitectura: El GIS no busca postes
        if ("LIGHT_POINTS".equalsIgnoreCase(layerCode)) {
            throw new IllegalArgumentException("El GIS no gestiona búsquedas de postes. Use light-point-service.");
        }

        // 2. Preparar punto de búsqueda (WGS84 -> SIRGAS)
        CoordinateDTO inputDto = new CoordinateDTO(lon, lat, 4326);
        CoordinateDTO converted = coordinateService.convert(inputDto, STORAGE_SRID);
        Point searchPoint = geometryFactory.createPoint(new Coordinate(converted.getX(), converted.getY()));
        searchPoint.setSRID(STORAGE_SRID);

        // 3. Buscar en SpatialFeature (Capas de referencia)
        List<SpatialFeature> features = featureRepository.findNearest(
                searchPoint,
                layerCode,
                PageRequest.of(0, limit)
        );

        // 4. Mapear respuesta
        return features.stream().map(f -> {
            double distance = f.getGeom().distance(searchPoint);
            return FeatureResponseDTO.builder()
                    .id(f.getId())
                    .externalId(f.getExternalId())
                    .properties(f.getProperties())
                    .distanceMeters(distance)
                    .location(new CoordinateDTO(f.getGeom().getCoordinate().x, f.getGeom().getCoordinate().y, STORAGE_SRID))
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * [NUEVO] Encuentra en qué distrito cae un punto.
     * Vital para asignar automáticamente el barrio a un reclamo o poste.
     */
    @Transactional(readOnly = true)
    public String findDistrictByPoint(Double lat, Double lon) {
        CoordinateDTO inputDto = new CoordinateDTO(lon, lat, 4326);
        CoordinateDTO converted = coordinateService.convert(inputDto, STORAGE_SRID);
        Point point = geometryFactory.createPoint(new Coordinate(converted.getX(), converted.getY()));
        point.setSRID(STORAGE_SRID);

        // Nota: Esto requiere un método en el repositorio, o traerlos y filtrar (si son pocos)
        // Para eficiencia, asumimos que son pocos distritos (<100) y filtramos en memoria
        // o implementamos una @Query con STIntersects en el repo.
        return districtRepository.findAll().stream()
                .filter(d -> d.getGeom().contains(point))
                .map(DistrictBoundary::getName)
                .findFirst()
                .orElse("Desconocido");
    }
}