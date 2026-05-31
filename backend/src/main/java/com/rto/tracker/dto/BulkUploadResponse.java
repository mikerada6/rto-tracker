package com.rto.tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkUploadResponse {
    private int totalRows;
    private int importedCount;
    private int skippedCount;
    private List<RowError> errors;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RowError {
        private int row;
        private String line;
        private String reason;
    }
}
