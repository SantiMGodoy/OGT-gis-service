package com.ogt.gis.service;

import com.ogt.gis.dto.CoordinateDTO;
import lombok.extern.slf4j.Slf4j;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CoordinateService {

    private final GeometryFactory geometryFactory = new GeometryFactory();

    /**
     * Convierte una coordenada de un SRID a otro.
     * @param sourceDto Coordenada de origen (X/Y/SRID)
     * @param targetSrid Código EPSG de destino (ej. 4326 = GPS)
     * @return Coordenada convertida
     */
    public CoordinateDTO convert(CoordinateDTO sourceDto, Integer targetSrid) {
        try {
            // 1. Sistemas de referencia
            CoordinateReferenceSystem sourceCRS =
                    CRS.decode("EPSG:" + sourceDto.getSrid(), true);

            CoordinateReferenceSystem targetCRS =
                    CRS.decode("EPSG:" + targetSrid, true);

            // 2. Transformación
            MathTransform transform =
                    CRS.findMathTransform(sourceCRS, targetCRS, true);

            // 3. Crear punto original
            Coordinate coord = new Coordinate(sourceDto.getX(), sourceDto.getY());
            Point sourcePoint = geometryFactory.createPoint(coord);

            // 4. Aplicar transformación
            Point targetPoint = (Point) JTS.transform(sourcePoint, transform);

            // 5. Log para depuración
            log.info("Conversión: ({}, {}) [EPSG:{}] -> ({}, {}) [EPSG:{}]",
                    sourceDto.getX(), sourceDto.getY(), sourceDto.getSrid(),
                    targetPoint.getX(), targetPoint.getY(), targetSrid
            );

            // 6. Retornar resultado
            return CoordinateDTO.builder()
                    .x(targetPoint.getX())
                    .y(targetPoint.getY())
                    .srid(targetSrid)
                    .build();

        } catch (Exception e) {
            log.error("Error al convertir coordenadas: {}", e.getMessage(), e);
            throw new RuntimeException("No se pudo convertir las coordenadas: " + e.getMessage());
        }
    }
}
