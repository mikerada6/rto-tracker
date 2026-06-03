package com.rto.tracker.service;

import com.rto.tracker.domain.*;
import com.rto.tracker.repository.OfficeDayRecordRepository;
import com.rto.tracker.repository.ZoneEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OfficeDayCalculationService {

    private final ZoneEventRepository eventRepository;
    private final OfficeDayRecordRepository recordRepository;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;

    public OfficeDayCalculationService(ZoneEventRepository eventRepository,
                                        OfficeDayRecordRepository recordRepository,
                                        MeterRegistry meterRegistry) {
        this.eventRepository = eventRepository;
        this.recordRepository = recordRepository;
        this.cacheHitCounter = Counter.builder("rto.officedayrecord.cache.hit")
                .description("OfficeDayRecord cache hits")
                .register(meterRegistry);
        this.cacheMissCounter = Counter.builder("rto.officedayrecord.cache.miss")
                .description("OfficeDayRecord cache misses")
                .register(meterRegistry);
    }

    @Transactional
    public OfficeDayRecord getOrCompute(UUID userId, User user, LocalDate date) {
        Optional<OfficeDayRecord> cached = recordRepository.findByUserIdAndDate(userId, date);
        if (cached.isPresent()) {
            log.debug("OfficeDayRecord cache hit: userId={}, date={}", userId, date);
            cacheHitCounter.increment();
            return cached.get();
        }

        log.debug("OfficeDayRecord cache miss: userId={}, date={}, computing...", userId, date);
        cacheMissCounter.increment();
        OfficeDayRecord record = compute(userId, user, date);
        log.info("OfficeDayRecord computed: userId={}, date={}, officesVisited={}, officeTimeSecs={}, commuteSecs={}",
                userId, date, record.getOfficesVisited().size(), record.getTotalOfficeTime(), record.getCommuteDuration());
        return recordRepository.save(record);
    }

    /**
     * Bulk version of ensureRecordsComputed. Fetches all existing records for the range in ONE
     * query, then computes and saves only the missing dates. Returns the full set of records.
     */
    @Transactional
    public List<OfficeDayRecord> ensureRangeComputed(User user, LocalDate start, LocalDate end) {
        LocalDate effectiveEnd = end.isAfter(LocalDate.now()) ? LocalDate.now() : end;
        if (start.isAfter(effectiveEnd)) {
            return Collections.emptyList();
        }

        // One query for all existing records
        List<OfficeDayRecord> existing = recordRepository.findByUserIdAndDateRange(
                user.getId(), start, effectiveEnd);
        Set<LocalDate> existingDates = existing.stream()
                .map(OfficeDayRecord::getDate)
                .collect(Collectors.toSet());

        // Compute only the missing dates
        List<OfficeDayRecord> computed = new ArrayList<>();
        LocalDate date = start;
        while (!date.isAfter(effectiveEnd)) {
            if (!existingDates.contains(date)) {
                log.debug("OfficeDayRecord cache miss: userId={}, date={}, computing...", user.getId(), date);
                cacheMissCounter.increment();
                OfficeDayRecord record = compute(user.getId(), user, date);
                log.info("OfficeDayRecord computed: userId={}, date={}, officesVisited={}, officeTimeSecs={}, commuteSecs={}",
                        user.getId(), date, record.getOfficesVisited().size(), record.getTotalOfficeTime(), record.getCommuteDuration());
                computed.add(record);
            } else {
                cacheHitCounter.increment();
            }
            date = date.plusDays(1);
        }

        if (!computed.isEmpty()) {
            recordRepository.saveAll(computed);
            existing = new ArrayList<>(existing);
            existing.addAll(computed);
            existing.sort(Comparator.comparing(OfficeDayRecord::getDate).reversed());
        }

        log.debug("ensureRangeComputed: userId={}, range={} to {}, existing={}, computed={}",
                user.getId(), start, effectiveEnd, existingDates.size(), computed.size());
        return existing;
    }

    @Observed(name = "rto.officedayrecord.compute",
            contextualName = "compute-office-day-record")
    public OfficeDayRecord compute(UUID userId, User user, LocalDate date) {
        ZoneId userZone = ZoneId.of(user.getTimezone());
        Instant dayStart = date.atStartOfDay(userZone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(userZone).toInstant();

        List<ZoneEvent> events = eventRepository.findByUserIdAndTimestampRange(userId, dayStart, dayEnd);

        Set<Zone> officesVisited = events.stream()
                .filter(e -> e.getZone().getType() == ZoneType.OFFICE && e.getEventType() == EventType.ENTER)
                .map(ZoneEvent::getZone)
                .collect(Collectors.toSet());

        long totalOfficeTime = calculateTotalOfficeTime(events, dayEnd);
        long commuteDuration = calculateCommuteDuration(events);
        Instant firstOfficeEntry = findFirstOfficeEntry(events);
        Instant lastOfficeExit = findLastOfficeExit(events);

        return OfficeDayRecord.builder()
                .user(user)
                .date(date)
                .totalOfficeTime(totalOfficeTime)
                .commuteDuration(commuteDuration)
                .firstOfficeEntry(firstOfficeEntry)
                .lastOfficeExit(lastOfficeExit)
                .officesVisited(officesVisited)
                .build();
    }

    long calculateTotalOfficeTime(List<ZoneEvent> events, Instant endOfDay) {
        Map<UUID, List<ZoneEvent>> eventsByZone = events.stream()
                .filter(e -> e.getZone().getType() == ZoneType.OFFICE)
                .collect(Collectors.groupingBy(
                        e -> e.getZone().getId(),
                        Collectors.toList()));

        long totalSeconds = 0;

        for (List<ZoneEvent> zoneEvents : eventsByZone.values()) {
            zoneEvents.sort(Comparator.comparing(ZoneEvent::getTimestamp));

            Instant enterTime = null;
            for (ZoneEvent event : zoneEvents) {
                if (event.getEventType() == EventType.ENTER) {
                    enterTime = event.getTimestamp();
                } else if (event.getEventType() == EventType.EXIT && enterTime != null) {
                    totalSeconds += Duration.between(enterTime, event.getTimestamp()).getSeconds();
                    enterTime = null;
                }
            }
            if (enterTime != null) {
                Instant eod = endOfDay.minusSeconds(1);
                totalSeconds += Duration.between(enterTime, eod).getSeconds();
            }
        }

        return totalSeconds;
    }

    long calculateCommuteDuration(List<ZoneEvent> events) {
        return calculateOutboundCommuteDuration(events) + calculateInboundCommuteDuration(events);
    }

    public long calculateOutboundCommuteDuration(List<ZoneEvent> events) {
        Instant homeExit = events.stream()
                .filter(e -> e.getZone().getType() == ZoneType.HOME && e.getEventType() == EventType.EXIT)
                .map(ZoneEvent::getTimestamp)
                .min(Comparator.naturalOrder())
                .orElse(null);

        Instant firstOfficeEnter = events.stream()
                .filter(e -> e.getZone().getType() == ZoneType.OFFICE && e.getEventType() == EventType.ENTER)
                .map(ZoneEvent::getTimestamp)
                .min(Comparator.naturalOrder())
                .orElse(null);

        if (homeExit != null && firstOfficeEnter != null && homeExit.isBefore(firstOfficeEnter)) {
            return Duration.between(homeExit, firstOfficeEnter).getSeconds();
        }
        return 0;
    }

    public long calculateInboundCommuteDuration(List<ZoneEvent> events) {
        Instant lastOfficeExit = events.stream()
                .filter(e -> e.getZone().getType() == ZoneType.OFFICE && e.getEventType() == EventType.EXIT)
                .map(ZoneEvent::getTimestamp)
                .max(Comparator.naturalOrder())
                .orElse(null);

        if (lastOfficeExit != null) {
            Instant homeEntry = events.stream()
                    .filter(e -> e.getZone().getType() == ZoneType.HOME && e.getEventType() == EventType.ENTER)
                    .map(ZoneEvent::getTimestamp)
                    .filter(t -> t.isAfter(lastOfficeExit))
                    .min(Comparator.naturalOrder())
                    .orElse(null);

            if (homeEntry != null) {
                return Duration.between(lastOfficeExit, homeEntry).getSeconds();
            }
        }
        return 0;
    }

    Instant findFirstOfficeEntry(List<ZoneEvent> events) {
        return events.stream()
                .filter(e -> e.getZone().getType() == ZoneType.OFFICE && e.getEventType() == EventType.ENTER)
                .map(ZoneEvent::getTimestamp)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    Instant findLastOfficeExit(List<ZoneEvent> events) {
        return events.stream()
                .filter(e -> e.getZone().getType() == ZoneType.OFFICE && e.getEventType() == EventType.EXIT)
                .map(ZoneEvent::getTimestamp)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    public List<String> buildCommuteRoute(List<ZoneEvent> events) {
        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        Instant homeExit = events.stream()
                .filter(e -> e.getZone().getType() == ZoneType.HOME && e.getEventType() == EventType.EXIT)
                .map(ZoneEvent::getTimestamp)
                .min(Comparator.naturalOrder())
                .orElse(null);

        Instant firstOfficeEnter = events.stream()
                .filter(e -> e.getZone().getType() == ZoneType.OFFICE && e.getEventType() == EventType.ENTER)
                .map(ZoneEvent::getTimestamp)
                .min(Comparator.naturalOrder())
                .orElse(null);

        if (homeExit == null || firstOfficeEnter == null) {
            return Collections.emptyList();
        }

        List<String> route = new ArrayList<>();
        events.stream()
                .filter(e -> e.getZone().getType() == ZoneType.HOME && e.getEventType() == EventType.EXIT)
                .min(Comparator.comparing(ZoneEvent::getTimestamp))
                .ifPresent(e -> route.add(e.getZone().getName()));

        events.stream()
                .filter(e -> e.getEventType() == EventType.ENTER)
                .filter(e -> e.getZone().getType() != ZoneType.HOME)
                .filter(e -> e.getZone().getType() != ZoneType.OFFICE)
                .filter(e -> !e.getTimestamp().isBefore(homeExit) && !e.getTimestamp().isAfter(firstOfficeEnter))
                .sorted(Comparator.comparing(ZoneEvent::getTimestamp))
                .forEach(e -> {
                    String name = e.getZone().getName();
                    if (route.isEmpty() || !route.getLast().equals(name)) {
                        route.add(name);
                    }
                });

        events.stream()
                .filter(e -> e.getZone().getType() == ZoneType.OFFICE && e.getEventType() == EventType.ENTER)
                .min(Comparator.comparing(ZoneEvent::getTimestamp))
                .ifPresent(e -> route.add(e.getZone().getName()));

        return route;
    }

    @Transactional
    public void invalidate(UUID userId, LocalDate date) {
        recordRepository.deleteByUserIdAndDate(userId, date);
        log.debug("OfficeDayRecord invalidated: userId={}, date={}", userId, date);
    }
}
