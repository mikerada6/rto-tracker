package com.rto.tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuarterReportResponse {

    private String period;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private int daysInOffice;
    private double averageDaysPerWeek;
    private boolean isCompliant;
    private List<MonthlyBreakdown> monthlyBreakdown;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthlyBreakdown {
        private String month;
        private int daysInOffice;
        private double averageDaysPerWeek;
    }
}
