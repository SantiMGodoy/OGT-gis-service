package com.ogt.gis.service;

import com.ogt.gis.dto.SpatialFeatureDTO;
import com.ogt.gis.entity.MapLayer;
import com.ogt.gis.entity.SpatialFeature;
import com.ogt.gis.repository.MapLayerRepository;
import com.ogt.gis.repository.SpatialFeatureRepository;
import com.ogt.gis.util.GeoJSONHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequenceFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service que expone operaciones CRUD y consultas espaciales retornando DTOs / GeoJSON-ready Maps
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpatialFeatureService {

    private final SpatialFeatureRepository featureRepository;
    private final MapLayerRepository layerRepository;
    private final SpatialQueryService spatialQueryService; // para nearest
    private final GeometryFactory gf = new GeometryFactory();

    // SRID de almacenamiento (mismo que usás en otros servicios)
    private static final int STORAGE_SRID = 31984;

    // --------------------------------------------------------
    // Lectura / mapeo entidad -> DTO / GeoJSON
    // --------------------------------------------------------
    @Transactional(readOnly = true)
    public List<SpatialFeatureDTO> listByLayer(String layerCode, Integer limit) {
        List<SpatialFeature> list = (limit == null) ?
                featureRepository.findByLayer_Code(layerCode) :
                featureRepository.findByLayer_Code(layerCode)
                        .stream().limit(limit).toList();

        return list.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listByLayerAsFeatureCollection(String layerCode, Integer limit) {
        List<SpatialFeatureDTO> dtos = listByLayer(layerCode, limit);
        Map<String, Object> fc = new LinkedHashMap<>();
        fc.put("type", "FeatureCollection");
        List<Object> features = dtos.stream().map(this::dtoToFeatureObject).collect(Collectors.toList());
        fc.put("features", features);
        return fc;
    }

    @Transactional(readOnly = true)
    public SpatialFeatureDTO getById(UUID id) {
        SpatialFeature sf = featureRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Feature not found: " + id));
        return toDTO(sf);
    }

    @Transactional
    public SpatialFeatureDTO create(String layerCode,
                                    Map<String,Object> geometryGeoJson,
                                    Map<String,Object> properties,
                                    String externalId,
                                    Integer inputSrid) {
        MapLayer layer = layerRepository.findByCode(layerCode)
                .orElseThrow(() -> new RuntimeException("Layer not found: " + layerCode));

        try {
            Geometry geom = GeoJSONHelper.geoJsonToGeometry(geometryGeoJson);
            if (geom == null) throw new RuntimeException("Geometry parse error");

            // reproyectar si el inputSrid difiere
            if (inputSrid != null && inputSrid != STORAGE_SRID) {
                var src = CRS.decode("EPSG:" + inputSrid, true);
                var dst = CRS.decode("EPSG:" + STORAGE_SRID, true);
                MathTransform transform = CRS.findMathTransform(src, dst, true);
                geom = org.geotools.geometry.jts.JTS.transform(geom, transform);
            }
            geom.setSRID(STORAGE_SRID);

            SpatialFeature sf = SpatialFeature.builder()
                    .layer(layer)
                    .externalId(externalId)
                    .geom(geom)
                    .properties(GeoJSONHelper.propertiesToJson(properties))
                    .createdAt(LocalDateTime.now())
                    .build();

            sf = featureRepository.save(sf);
            return toDTO(sf);

        } catch (Exception e) {
            log.error("Error creando feature", e);
            throw new RuntimeException("Error creando feature: " + e.getMessage(), e);
        }
    }

    @Transactional
    public SpatialFeatureDTO update(UUID id,
                                    Map<String,Object> geometryGeoJson,
                                    Map<String,Object> properties,
                                    String externalId,
                                    Integer inputSrid) {
        SpatialFeature sf = featureRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Feature not found: " + id));

        try {
            if (geometryGeoJson != null) {
                Geometry geom = GeoJSONHelper.geoJsonToGeometry(geometryGeoJson);
                if (inputSrid != null && inputSrid != STORAGE_SRID) {
                    var src = CRS.decode("EPSG:" + inputSrid, true);
                    var dst = CRS.decode("EPSG:" + STORAGE_SRID, true);
                    MathTransform transform = CRS.findMathTransform(src, dst, true);
                    geom = org.geotools.geometry.jts.JTS.transform(geom, transform);
                }
                geom.setSRID(STORAGE_SRID);
                sf.setGeom(geom);
            }

            if (properties != null) {
                sf.setProperties(GeoJSONHelper.propertiesToJson(properties));
            }

            if (externalId != null) sf.setExternalId(externalId);

            sf = featureRepository.save(sf);
            return toDTO(sf);

        } catch (Exception e) {
            log.error("Error actualizando feature", e);
            throw new RuntimeException("Error actualizando feature: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void delete(UUID id) {
        featureRepository.deleteById(id);
    }

    // --------------------------------------------------------
    // Spatial helpers
    // --------------------------------------------------------
    @Transactional(readOnly = true)
    public Map<String, Object> findNearestAsFeatureCollection(Double lat, Double lon, String layerCode, int limit) {
        // usa SpatialQueryService ya existente
        var nearest = spatialQueryService.findNearest(lat, lon, layerCode, limit);
        List<Object> features = nearest.stream()
                .map(dto -> {
                    Map<String, Object> feat = new LinkedHashMap<>();
                    feat.put("type", "Feature");
                    feat.put("id", dto.getId());
                    // location dto is stored in STORAGE_SRID coordinates
                    Map<String,Object> geom = new LinkedHashMap<>();
                    geom.put("type", "Point");
                    geom.put("coordinates", List.of(dto.getLocation().getX(), dto.getLocation().getY()));
                    feat.put("geometry", geom);
                    // properties: merge externalId and properties string (if any)
                    Map<String,Object> props = new LinkedHashMap<>();
                    props.put("externalId", dto.getExternalId());
                    if (dto.getProperties() != null) {
                        try {
                            Map<String,Object> parsed = GeoJSONHelper.parseProperties(dto.getProperties());
                            props.putAll(parsed);
                        } catch (Exception ignored) {}
                    }
                    props.put("distanceMeters", dto.getDistanceMeters());
                    feat.put("properties", props);
                    return feat;
                }).collect(Collectors.toList());

        Map<String, Object> fc = new LinkedHashMap<>();
        fc.put("type", "FeatureCollection");
        fc.put("features", features);
        return fc;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> intersectingAsFeatureCollection(Geometry filterGeom) {
        List<SpatialFeature> found = featureRepository.findIntersecting(filterGeom);
        List<Object> features = found.stream().map(this::entityToFeatureObject).collect(Collectors.toList());
        Map<String, Object> fc = new LinkedHashMap<>();
        fc.put("type", "FeatureCollection");
        fc.put("features", features);
        return fc;
    }

    @Transactional(readOnly = true)
    public long countByLayer(String layerCode) {
        return featureRepository.findByLayer_Code(layerCode).size();
    }

    // --------------------------------------------------------
    // Mappers
    // --------------------------------------------------------
    private SpatialFeatureDTO toDTO(SpatialFeature sf) {
        try {
            Map<String,Object> geom = GeoJSONHelper.geometryToGeoJson(sf.getGeom());
            Map<String,Object> props = null;
            if (sf.getProperties() != null) {
                props = GeoJSONHelper.parseProperties(sf.getProperties());
            }
            return SpatialFeatureDTO.builder()
                    .id(sf.getId())
                    .layerCode(sf.getLayer() != null ? sf.getLayer().getCode() : null)
                    .externalId(sf.getExternalId())
                    .geometry(geom)
                    .properties(props)
                    .createdAt(sf.getCreatedAt())
                    .build();
        } catch (Exception e) {
            log.error("Error mapeando feature -> dto", e);
            throw new RuntimeException("Error mapeando feature", e);
        }
    }

    private Map<String,Object> dtoToFeatureObject(SpatialFeatureDTO dto) {
        Map<String,Object> feat = new LinkedHashMap<>();
        feat.put("type", "Feature");
        feat.put("id", dto.getId());
        feat.put("geometry", dto.getGeometry());
        Map<String,Object> props = dto.getProperties() != null ? dto.getProperties() : new LinkedHashMap<>();
        props.put("externalId", dto.getExternalId());
        feat.put("properties", props);
        return feat;
    }

    private Map<String,Object> entityToFeatureObject(SpatialFeature sf) {
        SpatialFeatureDTO dto = toDTO(sf);
        return dtoToFeatureObject(dto);
    }
}
