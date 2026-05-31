package com.rto.tracker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rto.tracker.service.PeriodStatsCalculator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PeriodStatsResponse {

    private LocalDate periodStart;
    private LocalDate periodEnd;
    private int daysInOffice;
    private double averageDaysPerWeek;
    private double weeksRemaining;
    private int daysStillNeeded;
    private Double requiredAvgForRemainder;
    private Boolean isCompliant;

    public static PeriodStatsResponse from(PeriodStatsCalculator.PeriodStats stats) {
        return PeriodStatsResponse.builder()
                .periodStart(stats.periodStart())
                .periodEnd(stats.periodEnd())
                .daysInOffice(stats.daysInOffice())
                .averageDaysPerWeek(stats.averageDaysPerWeek())
                .weeksRemaining(stats.weeksRemaining())
                .daysStillNeeded(stats.daysStillNeeded())
                .requiredAvgForRemainder(stats.requiredAvgForRemainder())
                .build();
    }

    public static PeriodStatsResponse fromWithCompliance(PeriodStatsCalculator.PeriodStats stats) {
        PeriodStatsResponse response = from(stats);
        boolean periodEnded = stats.weeksRemaining() <= 0;
        response.setIsCompliant(periodEnded && stats.daysStillNeeded() == 0);
        return response;
    }
}
