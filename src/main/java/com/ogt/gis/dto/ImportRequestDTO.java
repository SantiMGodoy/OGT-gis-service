package com.ogt.gis.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class ImportRequestDTO {

    private MultipartFile file;

    private String layerCode;

    private String parameters; // Ej: "SRID:31984"
}
