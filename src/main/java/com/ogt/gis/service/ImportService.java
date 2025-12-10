package com.ogt.gis.service;

import com.ogt.gis.dto.ImportRequestDTO;
import com.ogt.gis.entity.ImportJob;
import com.ogt.gis.repository.ImportJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImportService {

    private final ImportJobRepository jobRepository;
    private final RabbitTemplate rabbitTemplate;

    // Carpeta temporal de imports
    private final Path tempDir = Path.of(System.getProperty("java.io.tmpdir"), "ogt-gis-imports");

    /**
     * Guarda el archivo (y si es zip, lo descomprime buscando .shp o .xlsx/.xls) y encola el job.
     * Devuelve jobId.
     */
    @Transactional
    public UUID queueImport(MultipartFile file, String layerCode, Integer srid) {
        try {
            if (!Files.exists(tempDir)) Files.createDirectories(tempDir);

            // Validaciones básicas
            if (file.isEmpty()) throw new RuntimeException("Archivo vacío");
            if (file.getSize() > 200L * 1024L * 1024L) // límite 200MB (ajustable)
                throw new RuntimeException("Archivo demasiado grande");

            String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
            String lowerName = original.toLowerCase();
            log.info("Original filename: {} (lower: {})", original, lowerName); // Log para depuración

            String filename = UUID.randomUUID() + "_" + original;
            Path target = tempDir.resolve(filename);
            file.transferTo(target.toFile());

            // Determinar la ruta final del archivo a procesar
            Path finalPath = target;

            // Solo validar como ZIP si el archivo termina en .zip
            if (lowerName.endsWith(".zip")) {
                if (isZipFile(target)) {
                    finalPath = extractFromZip(target); // Modificado para manejar .shp o .xlsx
                    if (finalPath == null) {
                        throw new RuntimeException("ZIP no contiene archivo .shp o .xlsx válido");
                    }
                }
            } else {
                // Validar que sea una extensión soportada
                if (!lowerName.endsWith(".shp") &&
                        !lowerName.endsWith(".geojson") &&
                        !lowerName.endsWith(".json") &&
                        !lowerName.endsWith(".kml") &&
                        !lowerName.endsWith(".xlsx") &&
                        !lowerName.endsWith(".xls")) {
                    throw new RuntimeException("Formato no soportado. Usar: .zip, .shp, .geojson, .kml, .xlsx o .xls");
                }
            }

            // Crear registro del Job
            ImportJob job = ImportJob.builder()
                    .jobType("IMPORT_SHAPEFILE")
                    .status("PENDING")
                    .fileUrl(finalPath.toAbsolutePath().toString())
                    .parameters("Layer: " + layerCode + ", SRID: " + (srid != null ? srid : "auto"))
                    .createdAt(LocalDateTime.now())
                    .build();

            jobRepository.save(job);

            // Enviar a RabbitMQ
            String message = job.getId() + ";" + layerCode;
            rabbitTemplate.convertAndSend("ogt.gis.events", "gis.import.queue", message);

            log.info("🚀 Importación encolada. Job ID: {} (file: {})", job.getId(), finalPath.getFileName());
            return job.getId();

        } catch (IOException e) {
            log.error("Error guardando archivo de importación", e);
            throw new RuntimeException("Error guardando archivo de importación: " + e.getMessage(), e);
        }
    }

    private boolean isZipFile(Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            byte[] sig = new byte[4];
            int r = is.read(sig);
            if (r < 4) return false;
            // PK\003\004
            return sig[0] == 'P' && sig[1] == 'K' && sig[2] == 3 && sig[3] == 4;
        }
    }

    /**
     * Extrae el primer conjunto .shp/.dbf/.shx (si existe) o .xlsx/.xls en el zip.
     * Devuelve la ruta al archivo principal extraído (.shp o .xlsx/.xls).
     */
    private Path extractFromZip(Path zipPath) throws IOException {
        Path outDir = tempDir.resolve("extracted_" + UUID.randomUUID());
        Files.createDirectories(outDir);

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            boolean hasShp = false;
            boolean hasExcel = false;
            Path excelPath = null;
            while ((entry = zis.getNextEntry()) != null) {
                String name = Path.of(entry.getName()).getFileName().toString();
                if (entry.isDirectory()) continue;
                String lower = name.toLowerCase();
                if (lower.endsWith(".shp") || lower.endsWith(".dbf") || lower.endsWith(".shx")
                        || lower.endsWith(".prj") || lower.endsWith(".cpg")) {
                    Path outFile = outDir.resolve(name);
                    try (OutputStream os = Files.newOutputStream(outFile)) {
                        zis.transferTo(os);
                    }
                    if (lower.endsWith(".shp")) hasShp = true;
                } else if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
                    Path outFile = outDir.resolve(name);
                    try (OutputStream os = Files.newOutputStream(outFile)) {
                        zis.transferTo(os);
                    }
                    hasExcel = true;
                    excelPath = outFile;
                }
            }

            if (hasShp) {
                try (var stream = Files.list(outDir)) {
                    return stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".shp"))
                            .findFirst()
                            .orElse(null);
                }
            } else if (hasExcel) {
                return excelPath;
            } else {
                // Limpieza si no hay nada válido
                Files.walk(outDir)
                        .sorted((a,b)->b.compareTo(a))
                        .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
                return null;
            }
        }
    }

    public ImportJob getStatus(String jobId) {
        return jobRepository.findById(UUID.fromString(jobId))
                .orElseThrow(() -> new RuntimeException("Job no encontrado: " + jobId));
    }
}