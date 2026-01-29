-- Actualización de la tabla import_jobs para soportar métricas de progreso y errores

ALTER TABLE import_jobs ADD total_rows INT;
ALTER TABLE import_jobs ADD rows_with_errors INT;
ALTER TABLE import_jobs ADD progress_percentage INT;
ALTER TABLE import_jobs ADD error_summary NVARCHAR(MAX);
ALTER TABLE import_jobs ADD processing_speed FLOAT;
