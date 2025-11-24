package com.ogt.gis.repository;

import com.ogt.gis.entity.SpatialFeature;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SpatialFeatureRepository extends JpaRepository<SpatialFeature, UUID> {

    // Consulta Espacial:
    // 1. Filtra por Capa (layerCode)
    // 2. Ordena por distancia ascendente (el más cercano primero)
    // Nota: 'distance' es una función estándar de Hibernate Spatial que se traduce a STDistance en SQL Server
    @Query("SELECT f FROM SpatialFeature f " +
            "JOIN f.layer l " +
            "WHERE l.code = :layerCode " +
            "ORDER BY distance(f.geom, :point) ASC")
    List<SpatialFeature> findNearest(
            @Param("point") Point point,
            @Param("layerCode") String layerCode,
            Pageable pageable
    );

    // Buscar features que intersectan con una geometría dada (ej. un barrio)
    @Query("SELECT f FROM SpatialFeature f WHERE intersects(f.geom, :filterGeom) = true")
    List<SpatialFeature> findIntersecting(@Param("filterGeom") Geometry filterGeom);
}