package com.rto.tracker.service;

import com.rto.tracker.dto.ReportExportData;
import com.rto.tracker.dto.ReportPeriod;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ReportExportServiceTest {

    private final ReportExportService service = new ReportExportService(null, null);

    @Test
    void week_resolvesToMondayThroughSunday() {
        // Wednesday Jun 10, 2026 → Mon Jun 8 – Sun Jun 14
        ReportExportService.Range r = service.resolveRange(
                ReportPeriod.WEEK, null, null, LocalDate.of(2026, 6, 10));
        assertThat(r.start()).isEqualTo(LocalDate.of(2026, 6, 8));
        assertThat(r.end()).isEqualTo(LocalDate.of(2026, 6, 14));
    }

    @Test
    void month_resolvesToFullCalendarMonth() {
        ReportExportService.Range r = service.resolveRange(
                ReportPeriod.MONTH, null, null, LocalDate.of(2026, 2, 15));
        assertThat(r.start()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(r.end()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void quarter_resolvesToFullCalendarQuarter() {
        ReportExportService.Range r = service.resolveRange(
                ReportPeriod.QUARTER, null, null, LocalDate.of(2026, 5, 5));
        assertThat(r.start()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(r.end()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    void year_resolvesToJanThroughDec() {
        ReportExportService.Range r = service.resolveRange(
                ReportPeriod.YEAR, null, null, LocalDate.of(2026, 7, 4));
        assertThat(r.start()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(r.end()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void custom_acceptsExplicitRange() {
        ReportExportService.Range r = service.resolveRange(
                ReportPeriod.CUSTOM,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 14),
                LocalDate.of(2026, 6, 10));
        assertThat(r.start()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(r.end()).isEqualTo(LocalDate.of(2026, 1, 14));
    }

    @Test
    void custom_requiresBothFromAndTo() {
        assertThatThrownBy(() -> service.resolveRange(
                ReportPeriod.CUSTOM, null, LocalDate.of(2026, 1, 14), LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Custom period requires");
    }

    @Test
    void custom_rejectsReversedRange() {
        assertThatThrownBy(() -> service.resolveRange(
                ReportPeriod.CUSTOM,
                LocalDate.of(2026, 1, 14),
                LocalDate.of(2026, 1, 1),
                LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'to' date must not be before");
    }

    @Test
    void custom_rejectsRangeOver365Days() {
        assertThatThrownBy(() -> service.resolveRange(
                ReportPeriod.CUSTOM,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2026, 1, 2), // 367 days inclusive
                LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum of 365 days");
    }

    @Test
    void custom_accepts365DayRangeExactly() {
        ReportExportService.Range r = service.resolveRange(
                ReportPeriod.CUSTOM,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                LocalDate.now());
        assertThat(r.start()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(r.end()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void summary_completePeriodPerfectlyOnTarget_isCompliant() {
        // 4 weeks @ 3.0/wk = 12 required, 12 in office. Period ends before today.
        ReportExportService.Range r = new ReportExportService.Range(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 28));
        ReportExportData.Summary s = service.buildSummary(r, 12, 3.0,
                LocalDate.of(2026, 7, 1));
        assertThat(s.daysInOffice()).isEqualTo(12);
        assertThat(s.requiredDays()).isEqualTo(12);
        assertThat(s.averagePerWeek()).isEqualTo(3.0);
        assertThat(s.compliancePercent()).isEqualTo(100);
        assertThat(s.compliant()).isTrue();
        assertThat(s.inProgress()).isFalse();
        assertThat(s.statusLabel()).isEqualTo("Met target");
    }

    @Test
    void summary_completePeriodBelowTarget_isNonCompliant() {
        ReportExportService.Range r = new ReportExportService.Range(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 28));
        ReportExportData.Summary s = service.buildSummary(r, 8, 3.0,
                LocalDate.of(2026, 7, 1));
        assertThat(s.averagePerWeek()).isEqualTo(2.0);
        assertThat(s.compliant()).isFalse();
        assertThat(s.compliancePercent()).isEqualTo(67); // 8 / 12 * 100
        assertThat(s.inProgress()).isFalse();
        assertThat(s.statusLabel()).isEqualTo("Below target");
    }

    @Test
    void summary_emptyRange_doesNotCrash() {
        ReportExportService.Range r = new ReportExportService.Range(
                LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 14));
        ReportExportData.Summary s = service.buildSummary(r, 0, 3.0,
                LocalDate.of(2026, 7, 1));
        assertThat(s.daysInOffice()).isZero();
        assertThat(s.compliant()).isFalse();
    }

    // --- In-progress periods ---

    @Test
    void summary_inProgressQuarter_atPace_showsOnPace() {
        // Q2 2026: Apr 1 – Jun 30. Today = May 27 (mid quarter). 8 weeks elapsed,
        // 5 weeks remaining. Pace = 24/8 = 3.0/wk → on pace.
        ReportExportService.Range r = new ReportExportService.Range(
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30));
        ReportExportData.Summary s = service.buildSummary(r, 24, 3.0,
                LocalDate.of(2026, 5, 27));
        assertThat(s.inProgress()).isTrue();
        assertThat(s.statusLabel()).isEqualTo("On pace");
        assertThat(s.compliant()).isTrue();
        assertThat(s.averagePerWeek()).isCloseTo(3.0, within(0.05));
        assertThat(s.weeksRemaining()).isCloseTo(5.0, within(0.1));
    }

    @Test
    void summary_inProgressQuarter_belowPace_showsBehindPace() {
        // Same quarter, 12 days in office instead of 24. Pace = 12/8 = 1.5/wk → behind.
        ReportExportService.Range r = new ReportExportService.Range(
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30));
        ReportExportData.Summary s = service.buildSummary(r, 12, 3.0,
                LocalDate.of(2026, 5, 27));
        assertThat(s.inProgress()).isTrue();
        assertThat(s.statusLabel()).isEqualTo("Behind pace");
        assertThat(s.compliant()).isFalse();
        // 27 still needed across ~5 weeks ≈ 5.4/wk
        assertThat(s.requiredPaceRemainder()).isCloseTo(5.4, within(0.2));
    }

    @Test
    void summary_inProgress_firstDayOfPeriod_treatedAsOnPace() {
        // Day 1 of the quarter: no time has elapsed yet, no data — don't flag
        // "Behind" just because nothing has happened.
        ReportExportService.Range r = new ReportExportService.Range(
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30));
        ReportExportData.Summary s = service.buildSummary(r, 0, 3.0,
                LocalDate.of(2026, 4, 1));
        assertThat(s.inProgress()).isTrue();
        assertThat(s.statusLabel()).isEqualTo("On pace");
    }

    @Test
    void summary_inProgress_compliancePercentReflectsPace_notFullPeriod() {
        // If we judged against full-period target, 12 days into Q2 would be
        // ~31% compliant. Pace-relative it should be 12/24 = 50%.
        ReportExportService.Range r = new ReportExportService.Range(
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30));
        ReportExportData.Summary s = service.buildSummary(r, 12, 3.0,
                LocalDate.of(2026, 5, 27));
        assertThat(s.compliancePercent()).isBetween(45, 55);
    }

    @Test
    void summary_futureRange_treatedAsNotInProgress() {
        // Anchor in the past producing a future quarter (edge case via custom).
        ReportExportService.Range r = new ReportExportService.Range(
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 3, 31));
        ReportExportData.Summary s = service.buildSummary(r, 0, 3.0,
                LocalDate.of(2026, 6, 10));
        assertThat(s.inProgress()).isFalse();
    }

    // --- Anchor-driven range resolution ---

    @Test
    void resolveRange_quarter_anchoredToPreviousQuarter_lateInYear() {
        // Today April 1 2026; user picks Q1 → anchor = March 15 2026.
        // Result: Jan 1 – Mar 31, 2026 (previous quarter, same year).
        ReportExportService.Range r = service.resolveRange(
                ReportPeriod.QUARTER, null, null, LocalDate.of(2026, 3, 15));
        assertThat(r.start()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(r.end()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void resolveRange_quarter_anchoredAcrossYearBoundary() {
        // Today Jan 1 2027; user picks "last quarter" → anchor = Oct 15 2026.
        // Result: Oct 1 – Dec 31, 2026 (previous year's Q4).
        ReportExportService.Range r = service.resolveRange(
                ReportPeriod.QUARTER, null, null, LocalDate.of(2026, 10, 15));
        assertThat(r.start()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(r.end()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void resolveRange_month_anchoredAcrossYearBoundary() {
        // Today Jan 1 2027; user picks "last month" → anchor = Dec 5 2026.
        ReportExportService.Range r = service.resolveRange(
                ReportPeriod.MONTH, null, null, LocalDate.of(2026, 12, 5));
        assertThat(r.start()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(r.end()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void resolveRange_year_usesAnchorYear() {
        ReportExportService.Range r = service.resolveRange(
                ReportPeriod.YEAR, null, null, LocalDate.of(2025, 7, 4));
        assertThat(r.start()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(r.end()).isEqualTo(LocalDate.of(2025, 12, 31));
    }

    @Test
    void resolveRange_week_anchoredAcrossYearBoundary() {
        // Today Jan 1 2027 (a Friday); user picks "last week" → anchor = Dec 25 2026.
        // Result: Mon Dec 21 – Sun Dec 27, 2026.
        ReportExportService.Range r = service.resolveRange(
                ReportPeriod.WEEK, null, null, LocalDate.of(2026, 12, 25));
        assertThat(r.start()).isEqualTo(LocalDate.of(2026, 12, 21));
        assertThat(r.end()).isEqualTo(LocalDate.of(2026, 12, 27));
    }

    @Test
    void weeklyBuckets_oneBucketPerCalendarWeek_andSumsCorrectly() {
        ReportExportService.Range r = new ReportExportService.Range(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 28));
        Set<LocalDate> qualifying = Set.of(
                LocalDate.of(2026, 6, 1),  // Mon, wk1
                LocalDate.of(2026, 6, 3),  // Wed, wk1
                LocalDate.of(2026, 6, 9),  // Tue, wk2
                LocalDate.of(2026, 6, 22)  // Mon, wk4
        );
        var buckets = service.buildWeeklyBuckets(r, qualifying);
        assertThat(buckets).hasSize(4);
        assertThat(buckets.get(0).daysInOffice()).isEqualTo(2);
        assertThat(buckets.get(1).daysInOffice()).isEqualTo(1);
        assertThat(buckets.get(2).daysInOffice()).isZero();
        assertThat(buckets.get(3).daysInOffice()).isEqualTo(1);
        // Max is 2; the wk-1 bar should be 100%, wk-2 50%
        assertThat(buckets.get(0).barHeightPercent()).isEqualTo(100);
        assertThat(buckets.get(1).barHeightPercent()).isEqualTo(50);
    }

    @Test
    void weeklyBuckets_shortRange_labelsEveryBucket() {
        ReportExportService.Range r = new ReportExportService.Range(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 28));
        var buckets = service.buildWeeklyBuckets(r, Set.of());
        assertThat(buckets).hasSize(4);
        assertThat(buckets).allMatch(ReportExportData.WeeklyBucket::showLabel);
    }

    @Test
    void weeklyBuckets_yearRange_thinsLabelsByFour() {
        ReportExportService.Range r = new ReportExportService.Range(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        var buckets = service.buildWeeklyBuckets(r, Set.of());
        // 53 buckets covering all ISO weeks the range touches
        assertThat(buckets.size()).isBetween(52, 54);
        assertThat(buckets.get(0).showLabel()).isTrue();
        assertThat(buckets.get(1).showLabel()).isFalse();
        assertThat(buckets.get(4).showLabel()).isTrue();
        assertThat(buckets.get(8).showLabel()).isTrue();
        // Only every 4th bucket carries a label
        long labelled = buckets.stream().filter(ReportExportData.WeeklyBucket::showLabel).count();
        assertThat(labelled).isBetween(13L, 14L);
    }

    @Test
    void labelStride_thresholds() {
        assertThat(com.rto.tracker.service.ReportExportService.labelStride(1)).isEqualTo(1);
        assertThat(com.rto.tracker.service.ReportExportService.labelStride(14)).isEqualTo(1);
        assertThat(com.rto.tracker.service.ReportExportService.labelStride(15)).isEqualTo(2);
        assertThat(com.rto.tracker.service.ReportExportService.labelStride(28)).isEqualTo(2);
        assertThat(com.rto.tracker.service.ReportExportService.labelStride(29)).isEqualTo(4);
        assertThat(com.rto.tracker.service.ReportExportService.labelStride(56)).isEqualTo(4);
        assertThat(com.rto.tracker.service.ReportExportService.labelStride(57)).isEqualTo(8);
    }

    @Test
    void weeklyBuckets_emptyQualifying_allZeroNoCrash() {
        ReportExportService.Range r = new ReportExportService.Range(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7));
        var buckets = service.buildWeeklyBuckets(r, Set.of());
        assertThat(buckets).hasSize(1);
        assertThat(buckets.get(0).daysInOffice()).isZero();
        assertThat(buckets.get(0).barHeightPercent()).isZero();
    }

    @Test
    void buildRows_rendersTimeInUserTimezone() {
        Map<LocalDate, ReportExportService.FirstEntry> firstEntries = new TreeMap<>();
        // 12:42 UTC == 8:42 AM in America/New_York (EDT, UTC-4)
        firstEntries.put(LocalDate.of(2026, 6, 10),
                new ReportExportService.FirstEntry(
                        Instant.parse("2026-06-10T12:42:00Z"), "Manhattan HQ"));
        var rows = service.buildRows(firstEntries, ZoneId.of("America/New_York"));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).firstEntryTime()).isEqualTo("8:42 AM");
        assertThat(rows.get(0).zoneName()).isEqualTo("Manhattan HQ");
        assertThat(rows.get(0).dayOfWeek()).isEqualTo("Wednesday");
    }

    @Test
    void formatPeriodLabel_humanFriendly() {
        assertThat(service.formatPeriodLabel(ReportPeriod.WEEK,
                new ReportExportService.Range(
                        LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 14))))
                .isEqualTo("Week of Jun 8, 2026");

        assertThat(service.formatPeriodLabel(ReportPeriod.MONTH,
                new ReportExportService.Range(
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))))
                .isEqualTo("June 2026");

        assertThat(service.formatPeriodLabel(ReportPeriod.QUARTER,
                new ReportExportService.Range(
                        LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30))))
                .isEqualTo("Q2 2026");

        assertThat(service.formatPeriodLabel(ReportPeriod.YEAR,
                new ReportExportService.Range(
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))))
                .isEqualTo("2026");

        assertThat(service.formatPeriodLabel(ReportPeriod.CUSTOM,
                new ReportExportService.Range(
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31))))
                .isEqualTo("Jan 1, 2026 — Mar 31, 2026");
    }
}
