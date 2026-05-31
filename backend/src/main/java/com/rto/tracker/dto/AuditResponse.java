package com.rto.tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditResponse {

    private LocalDate startDate;
    private LocalDate endDate;
    private int totalOfficeDays;
    private List<AuditDayEntry> days;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AuditDayEntry {
        private LocalDate date;
        private List<String> officesVisited;
        private String totalOfficeTime;
        private Long totalOfficeTimeSeconds;
        private Instant firstOfficeEntry;
        private Instant lastOfficeExit;
    }
}
