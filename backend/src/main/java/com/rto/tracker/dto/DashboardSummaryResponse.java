package com.rto.tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardSummaryResponse {

    private LocalDate asOf;
    private BigDecimal requiredAveragePerWeek;
    private PeriodStatsResponse week;
    private PeriodStatsResponse month;
    private PeriodStatsResponse quarter;
    private PeriodStatsResponse year;
    private List<RecentCommute> recentCommutes;
    /** Per-day office presence for the current quarter, used by the weekly bar chart. */
    private List<QuarterDayEntry> quarterOfficeDays;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecentCommute {
        private LocalDate date;
        private String duration;
        private String outboundDuration;
        private String inboundDuration;
        private String route;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuarterDayEntry {
        private LocalDate date;
        private long totalOfficeTimeSeconds;
    }
}
