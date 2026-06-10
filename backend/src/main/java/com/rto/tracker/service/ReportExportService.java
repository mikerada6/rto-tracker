package com.rto.tracker.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.rto.tracker.domain.EventType;
import com.rto.tracker.domain.User;
import com.rto.tracker.domain.Zone;
import com.rto.tracker.domain.ZoneEvent;
import com.rto.tracker.domain.ZoneType;
import com.rto.tracker.dto.ReportExportData;
import com.rto.tracker.dto.ReportPeriod;
import com.rto.tracker.repository.ZoneEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportExportService {

    static final int MAX_CUSTOM_RANGE_DAYS = 365;

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("h:mm a", Locale.US);
    private static final DateTimeFormatter DATE_LABEL_FORMATTER =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter GENERATED_FORMATTER =
            DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a z", Locale.US);

    private final ZoneEventRepository eventRepository;
    private final TemplateEngine templateEngine;

    public Range resolveRange(ReportPeriod period, LocalDate from, LocalDate to, LocalDate anchor) {
        return switch (period) {
            case WEEK -> {
                LocalDate start = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new Range(start, start.plusDays(6));
            }
            case MONTH -> {
                YearMonth ym = YearMonth.from(anchor);
                yield new Range(ym.atDay(1), ym.atEndOfMonth());
            }
            case QUARTER -> {
                int q = (anchor.getMonthValue() - 1) / 3;
                LocalDate start = LocalDate.of(anchor.getYear(), q * 3 + 1, 1);
                yield new Range(start, start.plusMonths(3).minusDays(1));
            }
            case YEAR -> new Range(
                    LocalDate.of(anchor.getYear(), 1, 1),
                    LocalDate.of(anchor.getYear(), 12, 31)
            );
            case CUSTOM -> {
                if (from == null || to == null) {
                    throw new IllegalArgumentException("Custom period requires both 'from' and 'to' dates");
                }
                if (to.isBefore(from)) {
                    throw new IllegalArgumentException("'to' date must not be before 'from' date");
                }
                long span = ChronoUnit.DAYS.between(from, to) + 1;
                if (span > MAX_CUSTOM_RANGE_DAYS) {
                    throw new IllegalArgumentException(
                            "Custom range exceeds maximum of " + MAX_CUSTOM_RANGE_DAYS + " days");
                }
                yield new Range(from, to);
            }
        };
    }

    public byte[] generatePdf(User user, ReportPeriod period, LocalDate from, LocalDate to, LocalDate anchor) {
        LocalDate today = LocalDate.now();
        LocalDate effectiveAnchor = anchor != null ? anchor : today;
        Range range = resolveRange(period, from, to, effectiveAnchor);
        ZoneId userZone = ZoneId.of(user.getTimezone());

        Instant startInstant = range.start().atStartOfDay(userZone).toInstant();
        Instant endInstant = range.end().plusDays(1).atStartOfDay(userZone).toInstant();

        List<ZoneEvent> events = eventRepository.findByUserIdAndTimestampRange(
                user.getId(), startInstant, endInstant);

        Map<LocalDate, FirstEntry> firstEntries = collectFirstOfficeEntries(events, userZone);

        ReportExportData data = ReportExportData.builder()
                .displayName(user.getDisplayName())
                .periodLabel(formatPeriodLabel(period, range))
                .periodStart(range.start())
                .periodEnd(range.end())
                .generatedAt(GENERATED_FORMATTER.format(ZonedDateTime.now(userZone)))
                .asOfDate(today)
                .rows(buildRows(firstEntries, userZone))
                .weeklyBuckets(buildWeeklyBuckets(range, firstEntries.keySet()))
                .summary(buildSummary(range, firstEntries.size(),
                        user.getRequiredDaysPerWeek().doubleValue(), today))
                .build();

        return render(data);
    }

    Map<LocalDate, FirstEntry> collectFirstOfficeEntries(List<ZoneEvent> events, ZoneId userZone) {
        Map<LocalDate, FirstEntry> result = new TreeMap<>();
        for (ZoneEvent event : events) {
            if (event.getEventType() != EventType.ENTER) continue;
            Zone zone = event.getZone();
            if (zone == null || zone.getType() != ZoneType.OFFICE) continue;
            LocalDate localDate = event.getTimestamp().atZone(userZone).toLocalDate();
            FirstEntry existing = result.get(localDate);
            if (existing == null || event.getTimestamp().isBefore(existing.timestamp())) {
                result.put(localDate, new FirstEntry(event.getTimestamp(), zone.getName()));
            }
        }
        return result;
    }

    List<ReportExportData.DayRow> buildRows(Map<LocalDate, FirstEntry> firstEntries, ZoneId userZone) {
        return firstEntries.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> ReportExportData.DayRow.builder()
                        .date(e.getKey())
                        .dayOfWeek(e.getKey().getDayOfWeek()
                                .getDisplayName(TextStyle.FULL, Locale.US))
                        .firstEntryTime(TIME_FORMATTER.format(
                                e.getValue().timestamp().atZone(userZone)))
                        .zoneName(e.getValue().zoneName())
                        .build())
                .toList();
    }

    List<ReportExportData.WeeklyBucket> buildWeeklyBuckets(Range range, java.util.Set<LocalDate> qualifyingDays) {
        Map<LocalDate, Integer> weekly = new LinkedHashMap<>();
        LocalDate cursor = range.start().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate finalWeek = range.end().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        while (!cursor.isAfter(finalWeek)) {
            weekly.put(cursor, 0);
            cursor = cursor.plusWeeks(1);
        }
        for (LocalDate day : qualifyingDays) {
            LocalDate weekStart = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            weekly.merge(weekStart, 1, Integer::sum);
        }
        int max = weekly.values().stream().max(Comparator.naturalOrder()).orElse(0);
        int stride = labelStride(weekly.size());
        List<ReportExportData.WeeklyBucket> buckets = new ArrayList<>();
        int index = 0;
        for (Map.Entry<LocalDate, Integer> e : weekly.entrySet()) {
            int height = max == 0 ? 0 : Math.round((e.getValue() * 100f) / max);
            buckets.add(ReportExportData.WeeklyBucket.builder()
                    .label(weekLabel(e.getKey()))
                    .daysInOffice(e.getValue())
                    .barHeightPercent(height)
                    .showLabel(index % stride == 0)
                    .build());
            index++;
        }
        return buckets;
    }

    static int labelStride(int bucketCount) {
        if (bucketCount <= 14) return 1;
        if (bucketCount <= 28) return 2;
        if (bucketCount <= 56) return 4;
        return 8;
    }

    ReportExportData.Summary buildSummary(Range range, int daysInOffice, double requiredDaysPerWeek) {
        long totalDays = ChronoUnit.DAYS.between(range.start(), range.end()) + 1;
        double totalWeeks = totalDays / 7.0;
        int requiredDays = (int) Math.ceil(requiredDaysPerWeek * totalWeeks);

        boolean inProgress = range.end().isAfter(today) && !range.start().isAfter(today);
        // Exclusive count of days completed *before* today, so day 1 of a period
        // has 0 elapsed days. Aligns "pace so far" with completed weeks.
        long elapsedDays = inProgress
                ? Math.max(0, ChronoUnit.DAYS.between(range.start(), today))
                : totalDays;
        double weeksElapsed = elapsedDays / 7.0;
        // Inclusive remaining: today itself is still an opportunity.
        long remainingDays = inProgress
                ? Math.max(0, ChronoUnit.DAYS.between(today, range.end()) + 1)
                : 0;
        double weeksRemaining = remainingDays / 7.0;

        double pacePerWeek = weeksElapsed > 0 ? daysInOffice / weeksElapsed : 0.0;
        int daysStillNeeded = Math.max(0, requiredDays - daysInOffice);
        Double requiredPaceRemainder = (inProgress && weeksRemaining > 0 && daysStillNeeded > 0)
                ? round(daysStillNeeded / weeksRemaining, 2)
                : null;

        int compliancePercent;
        if (inProgress) {
            // Pace-relative: actual vs what we'd expect at target pace by now.
            int expectedSoFar = (int) Math.ceil(requiredDaysPerWeek * weeksElapsed);
            compliancePercent = expectedSoFar == 0
                    ? 100
                    : (int) Math.round((daysInOffice * 100.0) / expectedSoFar);
        } else {
            compliancePercent = requiredDays == 0
                    ? 100
                    : (int) Math.round((daysInOffice * 100.0) / requiredDays);
        }

        boolean compliant;
        String statusLabel;
        if (inProgress) {
            // "On pace" if the employee can still hit target by maintaining
            // the required rate through the remaining days.
            int maxCatchupAtTargetPace = (int) Math.ceil(requiredDaysPerWeek * weeksRemaining);
            compliant = daysStillNeeded <= maxCatchupAtTargetPace;
            statusLabel = compliant ? "On pace" : "Behind pace";
        } else {
            compliant = daysInOffice >= requiredDays;
            statusLabel = compliant ? "Met target" : "Below target";
        }

        return ReportExportData.Summary.builder()
                .daysInOffice(daysInOffice)
                .requiredDays(requiredDays)
                .totalWeeks(round(totalWeeks, 1))
                .requiredDaysPerWeek(requiredDaysPerWeek)
                .averagePerWeek(round(pacePerWeek, 2))
                .compliancePercent(Math.min(compliancePercent, 999))
                .compliant(compliant)
                .inProgress(inProgress)
                .statusLabel(statusLabel)
                .weeksRemaining(round(weeksRemaining, 1))
                .daysStillNeeded(daysStillNeeded)
                .requiredPaceRemainder(requiredPaceRemainder)
                .build();
    }

    String formatPeriodLabel(ReportPeriod period, Range range) {
        return switch (period) {
            case WEEK -> "Week of " + DATE_LABEL_FORMATTER.format(range.start());
            case MONTH -> range.start().getMonth().getDisplayName(TextStyle.FULL, Locale.US)
                    + " " + range.start().getYear();
            case QUARTER -> "Q" + ((range.start().getMonthValue() - 1) / 3 + 1)
                    + " " + range.start().getYear();
            case YEAR -> String.valueOf(range.start().getYear());
            case CUSTOM -> DATE_LABEL_FORMATTER.format(range.start())
                    + " — " + DATE_LABEL_FORMATTER.format(range.end());
        };
    }

    private String weekLabel(LocalDate weekStart) {
        return DateTimeFormatter.ofPattern("MMM d", Locale.US).format(weekStart);
    }

    private byte[] render(ReportExportData data) {
        Context ctx = new Context(Locale.US);
        ctx.setVariable("data", data);
        ctx.setVariable("summary", data.summary());
        String html = templateEngine.process("report", ctx);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to render PDF report", e);
        }
    }

    private static double round(double value, int places) {
        double factor = Math.pow(10, places);
        return Math.round(value * factor) / factor;
    }

    public record Range(LocalDate start, LocalDate end) {}

    record FirstEntry(Instant timestamp, String zoneName) {}
}
