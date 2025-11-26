package com.ogt.gis.service;

import com.ogt.gis.entity.DistrictBoundary;
import com.ogt.gis.repository.DistrictBoundaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DistrictService {

    private final DistrictBoundaryRepository repository;

    @Transactional(readOnly = true)
    public List<DistrictBoundary> getAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public DistrictBoundary getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Distrito no encontrado: " + id));
    }
}