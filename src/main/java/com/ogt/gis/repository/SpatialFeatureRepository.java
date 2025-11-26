package com.ogt.gis.repository;

import com.ogt.gis.entity.SpatialFeature;
import org.locationtech.jts.geom.Geometry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SpatialFeatureRepository extends JpaRepository<SpatialFeature, UUID> {

    // Para frontend (paginado)
    @Query("""
            SELECT f 
            FROM SpatialFeature f 
            JOIN f.layer l 
            WHERE l.code = :layerCode
            """)
    Page<SpatialFeature> findByLayerCode(
            @Param("layerCode") String layerCode,
            Pageable pageable
    );


    // Para exportaciones internas
    @Query("""
            SELECT f 
            FROM SpatialFeature f 
            JOIN f.layer l 
            WHERE l.code = :layerCode
            """)
    List<SpatialFeature> findByLayerCode(
            @Param("layerCode") String layerCode
    );

    // Consulta espacial: intersección genérica
    @Query("""
            SELECT f 
            FROM SpatialFeature f 
            WHERE intersects(f.geom, :filterGeom) = true
            """)
    List<SpatialFeature> findIntersecting(
            @Param("filterGeom") Geometry filterGeom
    );

    //  Consulta espacial de proximidad (SQL Server + Hibernate Spatial)
    @Query("""
            SELECT f
            FROM SpatialFeature f
            JOIN f.layer l
            WHERE l.code = :layerCode
            ORDER BY distance(f.geom, :pt)
            """)
    List<SpatialFeature> findNearest(
            @Param("pt") Geometry point,
            @Param("layerCode") String layerCode,
            Pageable pageable
    );
}
