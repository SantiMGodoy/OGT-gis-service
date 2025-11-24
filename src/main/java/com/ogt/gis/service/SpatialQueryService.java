package com.ogt.gis.service;

import com.ogt.gis.dto.CoordinateDTO;
import com.ogt.gis.dto.FeatureResponseDTO;
import com.ogt.gis.entity.SpatialFeature;
import com.ogt.gis.repository.SpatialFeatureRepository;
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
    private final CoordinateService coordinateService;
    private final GeometryFactory geometryFactory = new GeometryFactory();

    // SRID de almacenamiento por defecto (SIRGAS 2000 UTM 24S)
    // En un sistema real, esto podría venir de la configuración de la capa
    private static final int STORAGE_SRID = 31984;

    @Transactional(readOnly = true)
    public List<FeatureResponseDTO> findNearest(Double lat, Double lon, String layerCode, int limit) {
        // 1. Crear el DTO de entrada (asumimos que el frontend manda GPS/WGS84)
        CoordinateDTO inputDto = CoordinateDTO.builder()
                .x(lon) // Longitud
                .y(lat) // Latitud
                .srid(4326) // GPS
                .build();

        // 2. Convertir al sistema de coordenadas de la BBDD (Proyección)
        // Esto es vital: transformamos grados a metros para poder buscar en SQL Server
        CoordinateDTO convertedDto = coordinateService.convert(inputDto, STORAGE_SRID);

        // 3. Crear el objeto Point de JTS para la consulta
        Point searchPoint = geometryFactory.createPoint(new Coordinate(convertedDto.getX(), convertedDto.getY()));
        searchPoint.setSRID(STORAGE_SRID);

        // 4. Consultar el repositorio
        List<SpatialFeature> features = featureRepository.findNearest(
                searchPoint,
                layerCode,
                PageRequest.of(0, limit)
        );

        // 5. Mapear a DTOs
        return features.stream().map(f -> {
            // Calcular distancia real (en metros, ya que ambos están proyectados)
            double distance = f.getGeom().distance(searchPoint);

            return FeatureResponseDTO.builder()
                    .id(f.getId())
                    .externalId(f.getExternalId())
                    .properties(f.getProperties())
                    .distanceMeters(distance)
                    .location(CoordinateDTO.builder()
                            .x(f.getGeom().getCoordinate().x)
                            .y(f.getGeom().getCoordinate().y)
                            .srid(STORAGE_SRID)
                            .build())
                    .build();
        }).collect(Collectors.toList());
    }
}