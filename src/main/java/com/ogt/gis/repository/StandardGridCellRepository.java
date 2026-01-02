package com.ogt.gis.repository;

import com.ogt.gis.entity.StandardGridCell;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StandardGridCellRepository extends JpaRepository<StandardGridCell, UUID> {

    /**
     * Busca todas las cuadrículas de una escala específica.
     */
    List<StandardGridCell> findByScale(String scale);

    /**
     * Obtiene todas las escalas disponibles.
     */
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT g.scale FROM StandardGridCell g ORDER BY g.scale")
    List<String> findDistinctScales();
}