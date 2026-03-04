package com.ogt.gis.service;

import com.ogt.gis.dto.CoordinateDTO;
import com.ogt.gis.entity.StandardGridCell;
import com.ogt.gis.repository.StandardGridCellRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
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

    /**
     * Generates a cartographic grid.
     *
     * The origin (originX, originY) is expected in WGS84 (longitude, latitude).
     * Width and height are in meters.
     *
     * The grid is built in projected UTM space (accurate meter-based cells),
     * then each cell polygon is transformed back to WGS84 (EPSG:4326) for storage
     * so that GeoJSON output works directly with Leaflet.
     */
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

        // Delete existing grids for this scale to avoid duplicates
        List<StandardGridCell> existing = gridRepository.findByScale(scaleName);
        if (!existing.isEmpty()) {
            gridRepository.deleteAll(existing);
            log.info("Deleted {} existing grid cells for scale '{}'", existing.size(), scaleName);
        }

        // Determine the UTM SRID for this longitude
        int utmSrid = (srid != null) ? srid : coordinateService.detectUTMZone(originX);
        log.info("Generating grid '{}' ({} rows x {} cols) using UTM EPSG:{}",
                scaleName, rows, cols, utmSrid);

        try {
            // Set up CRS transformations
            CoordinateReferenceSystem wgs84Crs = CRS.decode("EPSG:4326", true);
            CoordinateReferenceSystem utmCrs = CRS.decode("EPSG:" + utmSrid, true);
            MathTransform toUtm = CRS.findMathTransform(wgs84Crs, utmCrs, true);
            MathTransform toWgs84 = CRS.findMathTransform(utmCrs, wgs84Crs, true);

            // Convert WGS84 origin to UTM
            GeometryFactory utmFactory = new GeometryFactory(new PrecisionModel(), utmSrid);
            Point originWgs84 = utmFactory.createPoint(new Coordinate(originX, originY));
            Point originUtm = (Point) JTS.transform(originWgs84, toUtm);

            double utmOriginX = originUtm.getX();
            double utmOriginY = originUtm.getY();

            log.info("Origin WGS84 ({}, {}) -> UTM ({}, {})",
                    originX, originY, utmOriginX, utmOriginY);

            double cellWidth = width / cols;
            double cellHeight = height / rows;

            // WGS84 factory for the final stored geometry
            GeometryFactory wgs84Factory = new GeometryFactory(new PrecisionModel(), 4326);
            List<StandardGridCell> buffer = new ArrayList<>(rows * cols);

            for (int col = 0; col < cols; col++) {
                for (int row = 0; row < rows; row++) {

                    double x1 = utmOriginX + (col * cellWidth);
                    double y1 = utmOriginY + (row * cellHeight);
                    double x2 = x1 + cellWidth;
                    double y2 = y1 + cellHeight;

                    // Create polygon in UTM
                    Coordinate[] utmCoords = new Coordinate[]{
                            new Coordinate(x1, y1),
                            new Coordinate(x1, y2),
                            new Coordinate(x2, y2),
                            new Coordinate(x2, y1),
                            new Coordinate(x1, y1)
                    };
                    Polygon utmPolygon = utmFactory.createPolygon(utmCoords);

                    // Transform to WGS84
                    Polygon wgs84Polygon = (Polygon) JTS.transform(utmPolygon, toWgs84);
                    wgs84Polygon.setSRID(4326);

                    // Re-create with WGS84 factory to ensure SRID is set on all components
                    Coordinate[] wgsCoords = wgs84Polygon.getCoordinates();
                    Polygon storedPolygon = wgs84Factory.createPolygon(wgsCoords);

                    String gridCode = formatGridCode(col, row);

                    StandardGridCell cell = StandardGridCell.builder()
                            .scale(scaleName)
                            .gridCode(gridCode)
                            .geom(storedPolygon)
                            .lightPointsCount(0)
                            .build();

                    buffer.add(cell);
                }
            }

            gridRepository.saveAll(buffer);

            log.info("Grid '{}' generated with {} cells (stored in WGS84/EPSG:4326)",
                    scaleName, buffer.size());

            // Auto-recalculate light point counts for the new grid
            recalculateLightPointCountsForScale(scaleName);

        } catch (Exception e) {
            log.error("Error generating grid: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate grid: " + e.getMessage());
        }
    }

    /**
     * Recalculates light point counts for all grid cells.
     * Uses wgs_lat/wgs_lon from light_points table with a bounding box query.
     */
    @Transactional
    public int recalculateLightPointCounts() {
        log.info("Recalculating light point counts for all grids...");
        List<StandardGridCell> cells = gridRepository.findAll();
        return recalculateCells(cells);
    }

    /**
     * Recalculates light point counts only for cells of a specific scale.
     */
    @Transactional
    public int recalculateLightPointCountsForScale(String scale) {
        log.info("Recalculating light point counts for scale '{}'...", scale);
        List<StandardGridCell> cells = gridRepository.findByScale(scale);
        return recalculateCells(cells);
    }

    /**
     * Deletes all grid cells for a given scale.
     */
    @Transactional
    public int deleteGridsByScale(String scale) {
        List<StandardGridCell> cells = gridRepository.findByScale(scale);
        if (cells.isEmpty()) return 0;
        gridRepository.deleteAll(cells);
        log.info("Deleted {} grid cells for scale '{}'", cells.size(), scale);
        return cells.size();
    }

    // ================================================================
    // Internal helpers
    // ================================================================

    private int recalculateCells(List<StandardGridCell> cells) {
        int totalUpdated = 0;

        for (StandardGridCell cell : cells) {
            try {
                Geometry geom = cell.getGeom();
                if (geom == null) continue;

                Envelope env = geom.getEnvelopeInternal();

                // Use a simple bounding-box query on wgs_lat/wgs_lon columns.
                // This is efficient and works without a geometry column on light_points.
                String sql = """
                    SELECT COUNT(*)
                    FROM light_points lp
                    WHERE lp.wgs_lat IS NOT NULL
                      AND lp.wgs_lon IS NOT NULL
                      AND lp.wgs_lon >= :minX AND lp.wgs_lon <= :maxX
                      AND lp.wgs_lat >= :minY AND lp.wgs_lat <= :maxY
                """;

                Query query = entityManager.createNativeQuery(sql);
                query.setParameter("minX", env.getMinX());
                query.setParameter("maxX", env.getMaxX());
                query.setParameter("minY", env.getMinY());
                query.setParameter("maxY", env.getMaxY());

                Number result = (Number) query.getSingleResult();
                int count = result.intValue();

                Integer currentCount = cell.getLightPointsCount();
                if (currentCount == null || count != currentCount) {
                    cell.setLightPointsCount(count);
                    cell.setLastUpdated(LocalDateTime.now());
                    totalUpdated++;
                }

            } catch (Exception e) {
                log.error("Error processing cell {}: {}", cell.getGridCode(), e.getMessage());
            }
        }

        if (totalUpdated > 0) {
            gridRepository.saveAll(cells);
            log.info("Updated counts for {} cells (of {} total)", totalUpdated, cells.size());
        } else {
            log.info("No count changes detected");
        }

        return totalUpdated;
    }

    private void validateParameters(String scaleName, double width, double height, int rows, int cols) {
        if (scaleName == null || scaleName.isBlank()) {
            throw new IllegalArgumentException("Scale name cannot be empty.");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Width and height must be greater than zero.");
        }
        if (rows <= 0 || cols <= 0) {
            throw new IllegalArgumentException("Rows and columns must be greater than zero.");
        }
    }

    private String formatGridCode(int col, int row) {
        return String.format("%c%d", (char) ('A' + col), row + 1);
    }
}
