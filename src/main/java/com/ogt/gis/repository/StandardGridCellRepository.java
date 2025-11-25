package com.ogt.gis.repository;

import com.ogt.gis.entity.StandardGridCell;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StandardGridCellRepository extends JpaRepository<StandardGridCell, UUID > {
}
