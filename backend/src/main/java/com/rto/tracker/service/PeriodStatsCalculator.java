package com.rto.tracker.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class PeriodStatsCalculator {

    private PeriodStatsCalculator() {}

    public static PeriodStats calculate(LocalDate periodStart, LocalDate periodEnd,
                                         LocalDate today, int daysInOffice,
                                         BigDecimal requiredDaysPerWeek) {
        long totalPeriodDays = ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
        double totalPeriodWeeks = totalPeriodDays / 7.0;

        // Clamp today to period boundaries
        LocalDate effectiveToday = today;
        if (effectiveToday.isAfter(periodEnd)) {
            effectiveToday = periodEnd;
        }
        if (effectiveToday.isBefore(periodStart)) {
            effectiveToday = periodStart;
        }

        long daysSinceStart = ChronoUnit.DAYS.between(periodStart, effectiveToday) + 1;
        double weeksElapsed = daysSinceStart / 7.0;
        double weeksRemaining = totalPeriodWeeks - weeksElapsed;

        Double averageDaysPerWeek = weeksElapsed > 0
                ? round(daysInOffice / weeksElapsed, 2)
                : 0.0;

        double daysNeededTotal = requiredDaysPerWeek.doubleValue() * totalPeriodWeeks;
        int daysStillNeeded = Math.max(0, (int) Math.ceil(daysNeededTotal - daysInOffice));

        Double requiredAvgForRemainder = null;
        if (weeksRemaining > 0) {
            requiredAvgForRemainder = round(daysStillNeeded / weeksRemaining, 2);
        }

        return new PeriodStats(
                periodStart,
                periodEnd,
                daysInOffice,
                averageDaysPerWeek,
                round(weeksRemaining, 2),
                daysStillNeeded,
                requiredAvgForRemainder
        );
    }

    private static double round(double value, int places) {
        return BigDecimal.valueOf(value)
                .setScale(places, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public record PeriodStats(
            LocalDate periodStart,
            LocalDate periodEnd,
            int daysInOffice,
            double averageDaysPerWeek,
            double weeksRemaining,
            int daysStillNeeded,
            Double requiredAvgForRemainder
    ) {}
}
