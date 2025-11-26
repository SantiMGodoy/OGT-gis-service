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
     */
    public CoordinateDTO convert(CoordinateDTO sourceDto, Integer targetSrid) {
        try {
            CoordinateReferenceSystem sourceCRS =
                    CRS.decode("EPSG:" + sourceDto.getSrid(), true);

            CoordinateReferenceSystem targetCRS =
                    CRS.decode("EPSG:" + targetSrid, true);

            MathTransform transform =
                    CRS.findMathTransform(sourceCRS, targetCRS, true);

            Coordinate coord = new Coordinate(sourceDto.getX(), sourceDto.getY());
            Point sourcePoint = geometryFactory.createPoint(coord);

            Point targetPoint = (Point) JTS.transform(sourcePoint, transform);

            log.info("Conversión: ({}, {}) [EPSG:{}] -> ({}, {}) [EPSG:{}]",
                    sourceDto.getX(), sourceDto.getY(), sourceDto.getSrid(),
                    targetPoint.getX(), targetPoint.getY(), targetSrid
            );

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

    /**
     * ✅ NUEVO - Detecta la zona UTM correcta para Brasil según la longitud.
     *
     * Brasil usa 3 zonas UTM del sistema SIRGAS 2000:
     * - EPSG:31983 (UTM 23S): Longitud < -48° (Oeste)
     * - EPSG:31984 (UTM 24S): Longitud entre -48° y -42° (Centro) ← Viana, ES
     * - EPSG:31985 (UTM 25S): Longitud > -42° (Este)
     *
     * @param longitude Longitud en WGS84 (ej: -40.3128)
     * @return Código EPSG de la zona UTM correspondiente
     */
    public Integer detectUTMZone(Double longitude) {
        if (longitude == null) {
            throw new IllegalArgumentException("La longitud no puede ser nula");
        }

        if (longitude < -48.0) {
            log.debug("Longitud {} → Zona UTM 23S (EPSG:31983)", longitude);
            return 31983; // UTM Zona 23S
        } else if (longitude < -42.0) {
            log.debug("Longitud {} → Zona UTM 24S (EPSG:31984)", longitude);
            return 31984; // UTM Zona 24S (Viana está aquí)
        } else {
            log.debug("Longitud {} → Zona UTM 25S (EPSG:31985)", longitude);
            return 31985; // UTM Zona 25S
        }
    }

    /**
     * ✅ NUEVO - Obtiene información completa de una zona UTM.
     *
     * @param srid Código EPSG (31983, 31984 o 31985)
     * @return Mapa con información de la zona
     */
    public java.util.Map<String, Object> getUTMZoneInfo(Integer srid) {
        return switch (srid) {
            case 31983 -> java.util.Map.of(
                    "epsgCode", 31983,
                    "zoneName", "UTM 23S",
                    "longitudeRange", "< -48°",
                    "states", java.util.List.of("AC", "AM", "RO", "RR", "PA (oeste)")
            );
            case 31984 -> java.util.Map.of(
                    "epsgCode", 31984,
                    "zoneName", "UTM 24S",
                    "longitudeRange", "-48° a -42°",
                    "states", java.util.List.of("ES", "RJ", "SP", "MG", "GO", "DF", "MT", "MS", "PA (centro)")
            );
            case 31985 -> java.util.Map.of(
                    "epsgCode", 31985,
                    "zoneName", "UTM 25S",
                    "longitudeRange", "> -42°",
                    "states", java.util.List.of("BA", "SE", "AL", "PE", "PB", "RN", "CE", "PI", "MA")
            );
            default -> throw new IllegalArgumentException("SRID inválido para Brasil: " + srid + ". Use 31983, 31984 o 31985.");
        };
    }
}