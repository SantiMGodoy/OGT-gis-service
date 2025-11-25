package com.ogt.gis.controller;

import com.ogt.gis.dto.CreateFeatureRequest;
import com.ogt.gis.dto.SpatialFeatureDTO;
import com.ogt.gis.dto.UpdateFeatureRequest;
import com.ogt.gis.service.SpatialFeatureService;
import com.ogt.gis.service.SpatialQueryService;
import com.ogt.gis.service.CoordinateService;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTReader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Controller para operaciones de lectura/escritura de SpatialFeature
 */
@RestController
@RequestMapping("/api/gis")
@RequiredArgsConstructor
public class SpatialFeatureController {

    private final SpatialFeatureService featureService;
    private final SpatialQueryService spatialQueryService;
    private final CoordinateService coordinateService;

    // -------------------------
    // List / Read endpoints
    // -------------------------

    /**
     * FeatureCollection (GeoJSON) by layer
     */
    @GetMapping("/layers/{layerCode}/features")
    public ResponseEntity<?> listFeatures(
            @PathVariable String layerCode,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(featureService.listByLayerAsFeatureCollection(layerCode, limit));
    }

    /**
     * Count features by layer
     */
    @GetMapping("/layers/{layerCode}/count")
    public ResponseEntity<?> count(@PathVariable String layerCode) {
        return ResponseEntity.ok(Map.of("layerCode", layerCode, "count", featureService.countByLayer(layerCode)));
    }

    /**
     * Single feature by id (returns Feature JSON)
     */
    @GetMapping("/features/{id}")
    public ResponseEntity<?> getFeature(@PathVariable UUID id) {
        SpatialFeatureDTO dto = featureService.getById(id);
        Map<String, Object> feature = Map.of(
                "type", "Feature",
                "id", dto.getId(),
                "geometry", dto.getGeometry(),
                "properties", dto.getProperties()
        );
        return ResponseEntity.ok(feature);
    }

    // -------------------------
    // Nearest (delegates to SpatialQueryService)
    // -------------------------
    @GetMapping("/layers/{layerCode}/nearest")
    public ResponseEntity<?> nearest(
            @PathVariable String layerCode,
            @RequestParam Double lat,
            @RequestParam Double lon,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return ResponseEntity.ok(featureService.findNearestAsFeatureCollection(lat, lon, layerCode, limit));
    }

    // -------------------------
    // Intersect by polygon WKT (POST because WKT could be long)
    // -------------------------
    @PostMapping("/layers/{layerCode}/intersect")
    public ResponseEntity<?> intersect(
            @PathVariable String layerCode,
            @RequestBody String polygonWkt,
            @RequestParam(required = false) Integer srid // srid of the WKT (default assume STORAGE_SRID)
    ) {
        try {
            WKTReader reader = new WKTReader();
            Geometry geom = reader.read(polygonWkt);
            if (srid != null) geom.setSRID(srid);
            else geom.setSRID(31984);
            return ResponseEntity.ok(featureService.intersectingAsFeatureCollection(geom));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid WKT: " + e.getMessage()));
        }
    }

    // -------------------------
    // Create / Update / Delete
    // -------------------------
    @PostMapping("/features")
    public ResponseEntity<?> createFeature(
            @RequestParam String layerCode,
            @RequestParam(required = false, defaultValue = "4326") Integer inputSrid,
            @RequestBody CreateFeatureRequest dto
    ) {
        try {
            SpatialFeatureDTO created = featureService.create(
                    layerCode,
                    dto.getGeometry(),
                    dto.getProperties(),
                    dto.getExternalId(),
                    inputSrid
            );
            return ResponseEntity.status(201).body(created);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/features/{id}")
    public ResponseEntity<?> updateFeature(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "4326") Integer inputSrid,
            @RequestBody UpdateFeatureRequest dto
    ) {
        try {
            SpatialFeatureDTO updated = featureService.update(
                    id,
                    dto.getGeometry(),
                    dto.getProperties(),
                    dto.getExternalId(),
                    inputSrid
            );
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }


    @DeleteMapping("/features/{id}")
    public ResponseEntity<?> deleteFeature(@PathVariable UUID id) {
        featureService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
