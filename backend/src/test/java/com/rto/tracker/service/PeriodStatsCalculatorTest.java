package com.rto.tracker.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class PeriodStatsCalculatorTest {

    private static final BigDecimal REQUIRED = new BigDecimal("3.0");

    // --- Worked example from spec ---

    @Test
    void midQuarter_workedExample() {
        // Q2 2026: Apr 1 – Jun 30 = 91 days
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);
        LocalDate today = LocalDate.of(2026, 5, 29);

        PeriodStatsCalculator.PeriodStats stats =
                PeriodStatsCalculator.calculate(start, end, today, 23, REQUIRED);

        assertThat(stats.daysInOffice()).isEqualTo(23);
        assertThat(stats.averageDaysPerWeek()).isCloseTo(2.73, within(0.01));
        assertThat(stats.weeksRemaining()).isCloseTo(4.57, within(0.01));
        assertThat(stats.daysStillNeeded()).isEqualTo(16);
        assertThat(stats.requiredAvgForRemainder()).isCloseTo(3.50, within(0.01));
    }

    // --- Perfectly on target ---

    @Test
    void perfectlyOnTarget_daysStillNeededIsZero() {
        // Q2: 91 days = 13.0 weeks, need 39 days
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);
        LocalDate today = LocalDate.of(2026, 6, 30); // last day

        PeriodStatsCalculator.PeriodStats stats =
                PeriodStatsCalculator.calculate(start, end, today, 39, REQUIRED);

        assertThat(stats.daysStillNeeded()).isEqualTo(0);
        assertThat(stats.requiredAvgForRemainder()).isNull(); // no weeks remaining
    }

    // --- Behind pace ---

    @Test
    void behindPace_requiredAvgExceedsNormal() {
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);
        LocalDate today = LocalDate.of(2026, 5, 29);

        PeriodStatsCalculator.PeriodStats stats =
                PeriodStatsCalculator.calculate(start, end, today, 15, REQUIRED);

        assertThat(stats.requiredAvgForRemainder()).isGreaterThan(3.0);
    }

    // --- Ahead of pace ---

    @Test
    void aheadOfPace_daysStillNeededIsZero() {
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);
        LocalDate today = LocalDate.of(2026, 5, 29);

        // Already at 39 with weeks remaining
        PeriodStatsCalculator.PeriodStats stats =
                PeriodStatsCalculator.calculate(start, end, today, 39, REQUIRED);

        assertThat(stats.daysStillNeeded()).isEqualTo(0);
        assertThat(stats.requiredAvgForRemainder()).isEqualTo(0.0);
    }

    // --- Last day, exactly on target ---

    @Test
    void lastDayOfPeriod_exactlyOnTarget() {
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);
        LocalDate today = LocalDate.of(2026, 6, 30);

        PeriodStatsCalculator.PeriodStats stats =
                PeriodStatsCalculator.calculate(start, end, today, 39, REQUIRED);

        assertThat(stats.daysStillNeeded()).isEqualTo(0);
        // weeksRemaining = 0, so requiredAvgForRemainder should be null
        assertThat(stats.requiredAvgForRemainder()).isNull();
    }

    // --- Last day, one day short ---

    @Test
    void lastDayOfPeriod_oneDayShort() {
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);
        LocalDate today = LocalDate.of(2026, 6, 30);

        PeriodStatsCalculator.PeriodStats stats =
                PeriodStatsCalculator.calculate(start, end, today, 38, REQUIRED);

        assertThat(stats.daysStillNeeded()).isEqualTo(1);
        assertThat(stats.requiredAvgForRemainder()).isNull();
    }

    // --- Mathematically impossible ---

    @Test
    void mathematicallyImpossible_valueReturnedAsIs_noCapping() {
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);
        LocalDate today = LocalDate.of(2026, 6, 20); // 10 days left ~1.43 weeks

        // Only 5 days in office, need 39 total -> 34 still needed in ~1.43 weeks
        PeriodStatsCalculator.PeriodStats stats =
                PeriodStatsCalculator.calculate(start, end, today, 5, REQUIRED);

        assertThat(stats.requiredAvgForRemainder()).isGreaterThan(5.0);
    }

    // --- First day of period ---

    @Test
    void firstDayOfPeriod_zeroDaysIn() {
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);
        LocalDate today = LocalDate.of(2026, 4, 1);

        PeriodStatsCalculator.PeriodStats stats =
                PeriodStatsCalculator.calculate(start, end, today, 0, REQUIRED);

        // totalWeeks = 91/7 = 13.0, need ceil(3.0 * 13.0) = 39
        assertThat(stats.daysStillNeeded()).isEqualTo(39);
        assertThat(stats.daysInOffice()).isEqualTo(0);
    }

    // --- Week period on Monday ---

    @Test
    void weekPeriodOnMonday_correctFraction() {
        LocalDate monday = LocalDate.of(2026, 5, 25); // Monday
        LocalDate sunday = LocalDate.of(2026, 5, 31);

        PeriodStatsCalculator.PeriodStats stats =
                PeriodStatsCalculator.calculate(monday, sunday, monday, 0, REQUIRED);

        // daysSinceStart = 1, weeksElapsed = 1/7 = 0.14
        assertThat(stats.averageDaysPerWeek()).isCloseTo(0.0, within(0.01));
        // weeksRemaining = 1.0 - 0.14 = 0.86
        assertThat(stats.weeksRemaining()).isCloseTo(0.86, within(0.01));
    }

    // --- Month period ---

    @Test
    void monthPeriod_may2026() {
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 5, 31);
        LocalDate today = LocalDate.of(2026, 5, 15);

        // 31 days total, 15 elapsed
        PeriodStatsCalculator.PeriodStats stats =
                PeriodStatsCalculator.calculate(start, end, today, 10, REQUIRED);

        assertThat(stats.daysInOffice()).isEqualTo(10);
        double weeksElapsed = 15.0 / 7.0;
        assertThat(stats.averageDaysPerWeek()).isCloseTo(10.0 / weeksElapsed, within(0.01));
    }

    // --- Year period ---

    @Test
    void yearPeriod_midYear() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        LocalDate today = LocalDate.of(2026, 6, 30);

        // 181 days in (Jan 1 to Jun 30 inclusive)
        PeriodStatsCalculator.PeriodStats stats =
                PeriodStatsCalculator.calculate(start, end, today, 78, REQUIRED);

        double totalWeeks = 365.0 / 7.0;
        double weeksElapsed = 181.0 / 7.0;
        assertThat(stats.averageDaysPerWeek()).isCloseTo(78.0 / weeksElapsed, within(0.01));
        assertThat(stats.weeksRemaining()).isCloseTo(totalWeeks - weeksElapsed, within(0.01));
    }
}
