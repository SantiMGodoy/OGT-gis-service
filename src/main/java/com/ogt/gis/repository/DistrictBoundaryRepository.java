package com.ogt.gis.repository;
import com.ogt.gis.entity.DistrictBoundary;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface DistrictBoundaryRepository extends JpaRepository<DistrictBoundary, UUID> {
}