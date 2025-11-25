package com.ogt.gis.repository;

import com.ogt.gis.entity.ExportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ExportJobRepository extends JpaRepository<ExportJob, UUID> {
}
