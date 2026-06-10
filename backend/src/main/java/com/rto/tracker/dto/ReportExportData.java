package com.rto.tracker.dto;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Builder
public record ReportExportData(
        String displayName,
        String periodLabel,
        LocalDate periodStart,
        LocalDate periodEnd,
        String generatedAt,
        Summary summary,
        List<WeeklyBucket> weeklyBuckets,
        List<DayRow> rows
) {
    @Builder
    public record Summary(
            int daysInOffice,
            int requiredDays,
            double totalWeeks,
            double requiredDaysPerWeek,
            double averagePerWeek,
            int compliancePercent,
            boolean compliant
    ) {}

    @Builder
    public record WeeklyBucket(
            String label,
            int daysInOffice,
            int barHeightPercent
    ) {}

    @Builder
    public record DayRow(
            LocalDate date,
            String dayOfWeek,
            String firstEntryTime,
            String zoneName
    ) {}
}
