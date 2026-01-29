package com.ogt.gis.util;

import lombok.Data;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class ImportErrorLog {

    private List<ImportError> errors = new ArrayList<>();
    private Map<String, Integer> errorCounts = new HashMap<>();

    @Data
    public static class ImportError {
        private int rowIndex;
        private String errorType;
        private String message;
        private String fieldName;

        public ImportError(int rowIndex, String errorType, String message) {
            this.rowIndex = rowIndex;
            this.errorType = errorType;
            this.message = message;
        }

        public ImportError(int rowIndex, String errorType, String message, String fieldName) {
            this.rowIndex = rowIndex;
            this.errorType = errorType;
            this.message = message;
            this.fieldName = fieldName;
        }
    }

    public void addError(int rowIndex, String errorType, String message) {
        errors.add(new ImportError(rowIndex, errorType, message));
        errorCounts.merge(errorType, 1, Integer::sum);
    }

    public void addError(int rowIndex, String errorType, String message, String fieldName) {
        errors.add(new ImportError(rowIndex, errorType, message, fieldName));
        errorCounts.merge(errorType, 1, Integer::sum);
    }

    public int getErrorCount() {
        return errors.size();
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public String getSummary() {
        if (errors.isEmpty()) {
            return "Sin errores";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Total de errores: %d\n", errors.size()));

        errorCounts.forEach((type, count) ->
                sb.append(String.format("- %s: %d\n", type, count))
        );

        // Mostrar primeros 5 errores
        sb.append("\nPrimeros errores:\n");
        errors.stream()
                .limit(5)
                .forEach(e -> sb.append(String.format("Fila %d: %s\n", e.rowIndex, e.message)));

        if (errors.size() > 5) {
            sb.append(String.format("... y %d más\n", errors.size() - 5));
        }

        return sb.toString();
    }

    public String toJson() {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(Map.of(
                            "errorCount", getErrorCount(),
                            "errorTypes", errorCounts,
                            "errors", errors.stream().limit(10).toList()
                    ));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Error converting ImportErrorLog to JSON", e);
        }
    }
}