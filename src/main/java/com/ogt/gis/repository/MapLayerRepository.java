package com.ogt.gis.repository;
import com.ogt.gis.entity.MapLayer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface MapLayerRepository extends JpaRepository<MapLayer, UUID> {
    Optional<MapLayer> findByCode(String code);
    boolean existsByCode(String code);
}