package com.rto.tracker.service;

import com.rto.tracker.domain.OfficeDayRecord;
import com.rto.tracker.domain.User;
import com.rto.tracker.domain.ZoneEvent;
import com.rto.tracker.dto.DashboardSummaryResponse;
import com.rto.tracker.dto.PeriodStatsResponse;
import com.rto.tracker.dto.QuarterReportResponse;
import com.rto.tracker.repository.OfficeDayRecordRepository;
import com.rto.tracker.repository.ZoneEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DashboardService {

    private final OfficeDayRecordRepository recordRepository;
    private final OfficeDayCalculationService calculationService;
    private final ZoneEventRepository eventRepository;
    private final Counter dashboardRequestsCounter;

    public DashboardService(OfficeDayRecordRepository recordRepository,
                             OfficeDayCalculationService calculationService,
                             ZoneEventRepository eventRepository,
                             MeterRegistry meterRegistry) {
        this.recordRepository = recordRepository;
        this.calculationService = calculationService;
        this.eventRepository = eventRepository;
        this.dashboardRequestsCounter = Counter.builder("rto.dashboard.requests")
                .description("Number of dashboard summary requests")
                .register(meterRegistry);
    }

    @Transactional
    @Observed(name = "rto.dashboard.summary",
            contextualName = "get-dashboard-summary")
    public DashboardSummaryResponse getSummary(User user, LocalDate today) {
        dashboardRequestsCounter.increment();
        log.info("Computing dashboard summary: userId={}, asOf={}", user.getId(), today);
        BigDecimal required = user.getRequiredDaysPerWeek();

        // Period boundaries
        LocalDate weekStart = today.with(java.time.DayOfWeek.MONDAY);
        LocalDate weekEnd = today.with(java.time.DayOfWeek.SUNDAY);

        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.with(TemporalAdjusters.lastDayOfMonth());

        LocalDate quarterStart = getQuarterStart(today);
        LocalDate quarterEnd = getQuarterEnd(today);

        LocalDate yearStart = today.withDayOfYear(1);
        LocalDate yearEnd = LocalDate.of(today.getYear(), 12, 31);

        // Ensure all OfficeDayRecords are computed for the year range up to today
        ensureRecordsComputed(user, yearStart, today);

        // Count office days per period
        int weekDays = countOfficeDays(user.getId(), weekStart, today);
        int monthDays = countOfficeDays(user.getId(), monthStart, today);
        int quarterDays = countOfficeDays(user.getId(), quarterStart, today);
        int yearDays = countOfficeDays(user.getId(), yearStart, today);

        PeriodStatsResponse weekStats = PeriodStatsResponse.from(
                PeriodStatsCalculator.calculate(weekStart, weekEnd, today, weekDays, required));
        PeriodStatsResponse monthStats = PeriodStatsResponse.from(
                PeriodStatsCalculator.calculate(monthStart, monthEnd, today, monthDays, required));
        PeriodStatsResponse quarterStats = PeriodStatsResponse.fromWithCompliance(
                PeriodStatsCalculator.calculate(quarterStart, quarterEnd, today, quarterDays, required));
        PeriodStatsResponse yearStats = PeriodStatsResponse.from(
                PeriodStatsCalculator.calculate(yearStart, yearEnd, today, yearDays, required));

        List<DashboardSummaryResponse.RecentCommute> recentCommutes = getRecentCommutes(user, today);

        log.info("Dashboard summary computed: userId={}, weekDays={}, monthDays={}, quarterDays={}, yearDays={}",
                user.getId(), weekDays, monthDays, quarterDays, yearDays);

        return DashboardSummaryResponse.builder()
                .asOf(today)
                .requiredAveragePerWeek(required)
                .week(weekStats)
                .month(monthStats)
                .quarter(quarterStats)
                .year(yearStats)
                .recentCommutes(recentCommutes)
                .build();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Integer>> getAvailablePeriods(User user) {
        List<Object[]> rows = eventRepository.findDistinctYearQuartersByUserId(user.getId());
        return rows.stream().map(row -> {
            Map<String, Integer> m = new LinkedHashMap<>();
            m.put("year", ((Number) row[0]).intValue());
            m.put("quarter", ((Number) row[1]).intValue());
            return m;
        }).collect(Collectors.toList());
    }

    @Transactional
    public QuarterReportResponse getQuarterReport(User user, int year, int quarter) {
        log.info("Computing quarter report: userId={}, year={}, quarter={}", user.getId(), year, quarter);
        LocalDate quarterStart = getQuarterStartForYearQ(year, quarter);
        LocalDate quarterEnd = getQuarterEndForYearQ(year, quarter);
        LocalDate today = LocalDate.now();
        LocalDate effectiveEnd = today.isBefore(quarterEnd) ? today : quarterEnd;

        ensureRecordsComputed(user, quarterStart, effectiveEnd);

        int totalDays = countOfficeDays(user.getId(), quarterStart, effectiveEnd);

        PeriodStatsCalculator.PeriodStats stats = PeriodStatsCalculator.calculate(
                quarterStart, quarterEnd, today, totalDays, user.getRequiredDaysPerWeek());

        boolean periodComplete = !today.isBefore(quarterEnd);
        boolean isCompliant = periodComplete && stats.daysStillNeeded() == 0;

        // Monthly breakdown
        List<QuarterReportResponse.MonthlyBreakdown> monthly = new ArrayList<>();
        LocalDate monthIter = quarterStart;
        while (!monthIter.isAfter(quarterEnd)) {
            LocalDate mStart = monthIter.withDayOfMonth(1);
            LocalDate mEnd = monthIter.with(TemporalAdjusters.lastDayOfMonth());
            LocalDate mEffective = effectiveEnd.isBefore(mEnd) ? effectiveEnd : mEnd;

            if (!mStart.isAfter(effectiveEnd)) {
                int mDays = countOfficeDays(user.getId(), mStart, mEffective);
                long mTotalDays = ChronoUnit.DAYS.between(mStart, mEnd) + 1;
                double mWeeks = mTotalDays / 7.0;
                long mElapsedDays = ChronoUnit.DAYS.between(mStart, mEffective) + 1;
                double mWeeksElapsed = mElapsedDays / 7.0;
                double mAvg = mWeeksElapsed > 0 ? round(mDays / mWeeksElapsed, 2) : 0.0;

                monthly.add(QuarterReportResponse.MonthlyBreakdown.builder()
                        .month(mStart.getYear() + "-" + String.format("%02d", mStart.getMonthValue()))
                        .daysInOffice(mDays)
                        .averageDaysPerWeek(mAvg)
                        .build());
            }
            monthIter = monthIter.plusMonths(1);
        }

        log.info("Quarter report computed: userId={}, period={}-Q{}, daysInOffice={}, compliant={}",
                user.getId(), year, quarter, totalDays, isCompliant);

        return QuarterReportResponse.builder()
                .period(year + "-Q" + quarter)
                .periodStart(quarterStart)
                .periodEnd(quarterEnd)
                .daysInOffice(totalDays)
                .averageDaysPerWeek(stats.averageDaysPerWeek())
                .isCompliant(isCompliant)
                .monthlyBreakdown(monthly)
                .build();
    }

    private void ensureRecordsComputed(User user, LocalDate start, LocalDate end) {
        // Only compute for dates up to today
        LocalDate effectiveEnd = end.isAfter(LocalDate.now()) ? LocalDate.now() : end;
        LocalDate date = start;
        while (!date.isAfter(effectiveEnd)) {
            calculationService.getOrCompute(user.getId(), user, date);
            date = date.plusDays(1);
        }
    }

    private int countOfficeDays(UUID userId, LocalDate start, LocalDate end) {
        List<OfficeDayRecord> records = recordRepository.findByUserIdAndDateRange(userId, start, end);
        return (int) records.stream()
                .filter(r -> !r.getOfficesVisited().isEmpty())
                .count();
    }

    private List<DashboardSummaryResponse.RecentCommute> getRecentCommutes(User user, LocalDate today) {
        // Look back up to 30 days for recent commutes
        LocalDate lookback = today.minusDays(30);
        List<OfficeDayRecord> records = recordRepository.findByUserIdAndDateRange(user.getId(), lookback, today);

        return records.stream()
                .filter(r -> r.getCommuteDuration() != null && r.getCommuteDuration() > 0)
                .sorted(Comparator.comparing(OfficeDayRecord::getDate).reversed())
                .limit(5)
                .map(r -> {
                    // Build route for this day
                    Instant dayStart = r.getDate().atStartOfDay(ZoneId.of(user.getTimezone())).toInstant();
                    Instant dayEnd = r.getDate().plusDays(1).atStartOfDay(ZoneId.of(user.getTimezone())).toInstant();
                    List<ZoneEvent> events = eventRepository.findByUserIdAndTimestampRange(user.getId(), dayStart, dayEnd);
                    List<String> route = calculationService.buildCommuteRoute(events);
                    String routeStr = route.isEmpty() ? null : String.join(" \u2192 ", route);

                    long outbound = calculationService.calculateOutboundCommuteDuration(events);
                    long inbound = calculationService.calculateInboundCommuteDuration(events);

                    return DashboardSummaryResponse.RecentCommute.builder()
                            .date(r.getDate())
                            .duration(DurationFormatter.format(r.getCommuteDuration()))
                            .outboundDuration(outbound > 0 ? DurationFormatter.format(outbound) : null)
                            .inboundDuration(inbound > 0 ? DurationFormatter.format(inbound) : null)
                            .route(routeStr)
                            .build();
                })
                .toList();
    }

    public static LocalDate getQuarterStart(LocalDate date) {
        int month = ((date.getMonthValue() - 1) / 3) * 3 + 1;
        return LocalDate.of(date.getYear(), month, 1);
    }

    public static LocalDate getQuarterEnd(LocalDate date) {
        int endMonth = ((date.getMonthValue() - 1) / 3) * 3 + 3;
        return LocalDate.of(date.getYear(), endMonth, 1).with(TemporalAdjusters.lastDayOfMonth());
    }

    static LocalDate getQuarterStartForYearQ(int year, int quarter) {
        int month = (quarter - 1) * 3 + 1;
        return LocalDate.of(year, month, 1);
    }

    static LocalDate getQuarterEndForYearQ(int year, int quarter) {
        int endMonth = quarter * 3;
        return LocalDate.of(year, endMonth, 1).with(TemporalAdjusters.lastDayOfMonth());
    }

    private static double round(double value, int places) {
        return BigDecimal.valueOf(value)
                .setScale(places, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
