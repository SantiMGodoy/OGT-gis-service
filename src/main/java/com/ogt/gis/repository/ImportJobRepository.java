package com.ogt.gis.repository;
import com.ogt.gis.entity.ImportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {
}