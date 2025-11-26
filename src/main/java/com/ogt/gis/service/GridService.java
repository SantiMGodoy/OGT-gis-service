package com.ogt.gis.service;

import com.ogt.gis.entity.StandardGridCell;
import com.ogt.gis.repository.StandardGridCellRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GridService {

    private final StandardGridCellRepository gridRepository;
    private final CoordinateService coordinateService;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void generateGrid(String scaleName,
                             double originX,
                             double originY,
                             double width,
                             double height,
                             int rows,
                             int cols,
                             Integer srid) {

        validateParameters(scaleName, width, height, rows, cols);

        // ✅ Usar el SRID proporcionado (o detectarlo automáticamente)
        if (srid == null) {
            double centerLon = originX + (width / 2);
            srid = coordinateService.detectUTMZone(centerLon);
            log.info("🔍 SRID no especificado. Detectado automáticamente: EPSG:{}", srid);
        }

        // ✅ Crear GeometryFactory con el SRID correcto
        GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), srid);

        log.info("Iniciando generación de cuadrícula '{}' ({} filas × {} columnas) en EPSG:{}...",
                scaleName, rows, cols, srid);

        double cellWidth  = width / cols;
        double cellHeight = height / rows;

        List<StandardGridCell> buffer = new ArrayList<>(rows * cols);

        for (int col = 0; col < cols; col++) {
            for (int row = 0; row < rows; row++) {

                double x1 = originX + (col * cellWidth);
                double y1 = originY + (row * cellHeight);
                double x2 = x1 + cellWidth;
                double y2 = y1 + cellHeight;

                Coordinate[] coords = new Coordinate[]{
                        new Coordinate(x1, y1),
                        new Coordinate(x1, y2),
                        new Coordinate(x2, y2),
                        new Coordinate(x2, y1),
                        new Coordinate(x1, y1)
                };

                Polygon polygon = geometryFactory.createPolygon(coords);
                String gridCode = formatGridCode(col, row);

                StandardGridCell cell = StandardGridCell.builder()
                        .scale(scaleName)
                        .gridCode(gridCode)
                        .geom(polygon)
                        .lightPointsCount(0)
                        .build();

                buffer.add(cell);
            }
        }

        gridRepository.saveAll(buffer);

        log.info("✅ Cuadrícula '{}' generada correctamente con {} celdas en EPSG:{}.",
                scaleName, buffer.size(), srid);
    }

    /**
     * ✅ Recalcula el conteo de puntos de luz dentro de cada celda.
     */
    @Transactional
    public int recalculateLightPointCounts() {
        log.info("🔄 Iniciando recálculo de conteo de puntos por cuadrícula...");

        List<StandardGridCell> cells = gridRepository.findAll();
        int totalUpdated = 0;

        for (StandardGridCell cell : cells) {
            try {
                String sql = """
                    SELECT COUNT(*)
                    FROM light_points lp
                    WHERE lp.geom.STWithin(geometry::STGeomFromText(:wkt, :srid)) = 1
                """;

                Query query = entityManager.createNativeQuery(sql);
                query.setParameter("wkt", cell.getGeom().toText());
                query.setParameter("srid", cell.getGeom().getSRID());

                Number result = (Number) query.getSingleResult();
                int count = result.intValue();

                // ✅ CORRECCIÓN: Usar != en lugar de .equals()
                Integer currentCount = cell.getLightPointsCount();
                if (currentCount == null || count != currentCount) {
                    cell.setLightPointsCount(count);
                    cell.setLastUpdated(LocalDateTime.now());
                    totalUpdated++;
                }

            } catch (Exception e) {
                log.error("❌ Error procesando celda {}: {}", cell.getGridCode(), e.getMessage());
            }
        }

        if (totalUpdated > 0) {
            gridRepository.saveAll(cells);
            log.info("✅ Conteo actualizado para {} celdas (de {} totales)", totalUpdated, cells.size());
        } else {
            log.info("ℹ️ No hubo cambios en el conteo de celdas");
        }

        return totalUpdated;
    }

    // ================================================================
    // Helpers
    // ================================================================

    private void validateParameters(String scaleName, double width, double height, int rows, int cols) {
        if (scaleName == null || scaleName.isBlank()) {
            throw new IllegalArgumentException("El nombre de la escala no puede estar vacío.");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("El ancho y alto deben ser mayores que cero.");
        }
        if (rows <= 0 || cols <= 0) {
            throw new IllegalArgumentException("Filas y columnas deben ser mayores a cero.");
        }
    }

    private String formatGridCode(int col, int row) {
        return String.format("%c%d", (char) ('A' + col), row + 1);
    }
}