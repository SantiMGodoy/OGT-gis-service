package com.ogt.gis.service;

import com.ogt.gis.entity.StandardGridCell;
import com.ogt.gis.repository.StandardGridCellRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GridService {

    private final StandardGridCellRepository gridRepository;
    private final GeometryFactory geometryFactory =
            new GeometryFactory(new PrecisionModel(), 31984); // SIRGAS 2000 / UTM 24S

    @Transactional
    public void generateGrid(String scaleName,
                             double originX,
                             double originY,
                             double width,
                             double height,
                             int rows,
                             int cols) {

        validateParameters(scaleName, width, height, rows, cols);

        log.info("Iniciando generación de cuadrícula '{}' ({} filas × {} columnas)...",
                scaleName, rows, cols);

        double cellWidth  = width / cols;
        double cellHeight = height / rows;

        List<StandardGridCell> buffer = new ArrayList<>(rows * cols);

        // ------------------------------------------------------------
        // Construcción de celdas
        // ------------------------------------------------------------
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
                        new Coordinate(x1, y1) // cierre del polígono
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

        // ------------------------------------------------------------
        // Guardar en bloque (mejor rendimiento: 10× más rápido)
        // ------------------------------------------------------------
        gridRepository.saveAll(buffer);

        log.info("Cuadrícula '{}' generada correctamente con {} celdas.",
                scaleName, buffer.size());
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
        // Columna → letra A, B, C, ...
        // Fila → número 1, 2, 3, ...
        return String.format("%c%d", (char) ('A' + col), row + 1);
    }
}
