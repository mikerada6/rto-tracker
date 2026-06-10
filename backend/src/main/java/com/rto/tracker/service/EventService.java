package com.rto.tracker.service;

import com.rto.tracker.domain.*;
import com.rto.tracker.dto.BulkUploadResponse;
import com.rto.tracker.dto.CreateEventRequest;
import com.rto.tracker.exception.BusinessRuleException;
import com.rto.tracker.exception.EntityNotFoundException;
import com.rto.tracker.repository.OfficeDayRecordRepository;
import com.rto.tracker.repository.ZoneEventRepository;
import com.rto.tracker.repository.ZoneRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@Slf4j
public class EventService {

    private final ZoneEventRepository eventRepository;
    private final ZoneRepository zoneRepository;
    private final OfficeDayRecordRepository officeDayRecordRepository;
    private final Counter eventsRecordedCounter;
    private final Counter eventsDeduplicatedCounter;
    private final Counter eventsBounceDebouncedCounter;
    private final int deduplicationWindowMinutes;
    private final int bounceDebounceSeconds;

    public EventService(ZoneEventRepository eventRepository,
                        ZoneRepository zoneRepository,
                        OfficeDayRecordRepository officeDayRecordRepository,
                        MeterRegistry meterRegistry,
                        @Value("${app.deduplication-window-minutes:5}") int deduplicationWindowMinutes,
                        @Value("${app.bounce-debounce-seconds:30}") int bounceDebounceSeconds) {
        this.eventRepository = eventRepository;
        this.zoneRepository = zoneRepository;
        this.officeDayRecordRepository = officeDayRecordRepository;
        this.deduplicationWindowMinutes = deduplicationWindowMinutes;
        this.bounceDebounceSeconds = bounceDebounceSeconds;
        this.eventsRecordedCounter = Counter.builder("rto.events.recorded")
                .description("Number of zone events successfully recorded")
                .register(meterRegistry);
        this.eventsDeduplicatedCounter = Counter.builder("rto.events.deduplicated")
                .description("Number of zone events deduplicated")
                .register(meterRegistry);
        this.eventsBounceDebouncedCounter = Counter.builder("rto.events.bounce_debounced")
                .description("Number of zone events dropped as opposite-direction GPS bounces")
                .register(meterRegistry);
    }

    @Transactional
    public ZoneEvent recordEvent(UUID userId, User user, CreateEventRequest request) {
        // Validate timestamp is not in the future
        if (request.getTimestamp().isAfter(Instant.now())) {
            log.warn("Future timestamp rejected: userId={}, timestamp={}", userId, request.getTimestamp());
            throw new BusinessRuleException("Timestamp must not be in the future");
        }

        // Resolve zone
        Zone zone = resolveZone(userId, request);

        // Idempotency check
        Instant windowStart = request.getTimestamp().minusSeconds(deduplicationWindowMinutes * 60L);
        Instant windowEnd = request.getTimestamp().plusSeconds(deduplicationWindowMinutes * 60L);

        List<ZoneEvent> duplicates = eventRepository.findDuplicates(
                userId, zone.getId(), request.getEventType(), windowStart, windowEnd);

        if (!duplicates.isEmpty()) {
            log.warn("Duplicate event detected: userId={}, zoneId={}, eventType={}, timestamp={}",
                    userId, zone.getId(), request.getEventType(), request.getTimestamp());
            eventsDeduplicatedCounter.increment();
            return duplicates.getFirst();
        }

        // GPS bounce-debounce: drop opposite-direction events for the same user+zone
        // that arrive within the configured window (covers Exit->Enter and Enter->Exit jitter)
        Optional<ZoneEvent> latestForZone = eventRepository.findLatestForUserAndZone(
                userId, zone.getId(), request.getTimestamp());
        if (latestForZone.isPresent()) {
            ZoneEvent latest = latestForZone.get();
            long deltaSeconds = Math.abs(Duration.between(latest.getTimestamp(), request.getTimestamp()).getSeconds());
            if (latest.getEventType() != request.getEventType() && deltaSeconds <= bounceDebounceSeconds) {
                log.warn("Bounce-debounced event: userId={}, zoneId={}, incomingType={}, incomingTs={}, " +
                                "priorType={}, priorTs={}, deltaSeconds={}",
                        userId, zone.getId(), request.getEventType(), request.getTimestamp(),
                        latest.getEventType(), latest.getTimestamp(), deltaSeconds);
                eventsBounceDebouncedCounter.increment();
                return latest;
            }
        }

        ZoneEvent event = ZoneEvent.builder()
                .user(user)
                .zone(zone)
                .eventType(request.getEventType())
                .timestamp(request.getTimestamp())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .build();

        ZoneEvent saved = eventRepository.save(event);
        eventsRecordedCounter.increment();

        // Invalidate cached OfficeDayRecord for this date
        ZoneId userZone = ZoneId.of(user.getTimezone());
        LocalDate eventDate = saved.getTimestamp().atZone(userZone).toLocalDate();
        officeDayRecordRepository.deleteByUserIdAndDate(userId, eventDate);
        log.info("OfficeDayRecord cache invalidated: userId={}, date={}", userId, eventDate);

        log.info("Event recorded: id={}, userId={}, zoneId={}, type={}, timestamp={}",
                saved.getId(), userId, zone.getId(), saved.getEventType(), saved.getTimestamp());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ZoneEvent> listEvents(UUID userId, String timezone, LocalDate startDate, LocalDate endDate,
                                       UUID zoneId, EventType eventType) {
        ZoneId userZone = ZoneId.of(timezone);
        Instant start = startDate.atStartOfDay(userZone).toInstant();
        Instant end = endDate.plusDays(1).atStartOfDay(userZone).toInstant();

        List<ZoneEvent> events = eventRepository.findByUserIdAndTimestampRange(userId, start, end);

        return events.stream()
                .filter(e -> zoneId == null || e.getZone().getId().equals(zoneId))
                .filter(e -> eventType == null || e.getEventType() == eventType)
                .toList();
    }

    @Transactional
    public BulkUploadResponse bulkImport(User user, MultipartFile file, Map<String, String> zoneMapping) {
        UUID userId = user.getId();
        log.info("Bulk import: resolving {} zone mappings for userId={}", zoneMapping.size(), userId);

        // Pre-resolve all zones from the mapping
        Map<String, Zone> resolvedZones = new HashMap<>();
        for (Map.Entry<String, String> entry : zoneMapping.entrySet()) {
            String csvName = entry.getKey();
            String dbExternalId = entry.getValue();
            log.debug("Resolving zone mapping: csvName='{}' -> externalId='{}', userId={}", csvName, dbExternalId, userId);
            Zone zone = zoneRepository.findByUserIdAndExternalId(userId, dbExternalId)
                    .orElseThrow(() -> {
                        log.error("Zone resolution failed: externalId='{}' not found for userId={} (CSV name='{}')",
                                dbExternalId, userId, csvName);
                        return new EntityNotFoundException(
                            "Zone with externalId '" + dbExternalId + "' not found (mapped from CSV name '" + csvName + "')");
                    });
            log.debug("Zone resolved: csvName='{}' -> zone.id={}, zone.name='{}'", csvName, zone.getId(), zone.getName());
            resolvedZones.put(csvName, zone);
        }

        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("M/d/yy H:mm:ss");
        ZoneId eastern = ZoneId.of(user.getTimezone());

        List<ZoneEvent> eventsToSave = new ArrayList<>();
        List<BulkUploadResponse.RowError> errors = new ArrayList<>();
        Set<LocalDate> affectedDates = new HashSet<>();
        Instant minTimestamp = null;
        Instant maxTimestamp = null;
        int totalRows = 0;

        // Parse CSV
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String header = reader.readLine(); // skip header
            if (header == null) {
                return BulkUploadResponse.builder()
                        .totalRows(0).importedCount(0).skippedCount(0)
                        .errors(List.of()).build();
            }

            String line;
            int rowNum = 1;
            while ((line = reader.readLine()) != null) {
                rowNum++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                totalRows++;

                try {
                    String[] parts = trimmed.split(",", 4);
                    if (parts.length < 4) {
                        errors.add(BulkUploadResponse.RowError.builder()
                                .row(rowNum).line(trimmed).reason("Expected 4 comma-separated fields").build());
                        continue;
                    }

                    String dateStr = parts[0].trim();
                    String timeStr = parts[1].trim();
                    String csvZoneName = parts[2].trim();
                    String csvEventType = parts[3].trim();

                    // Parse timestamp
                    LocalDateTime localDateTime = LocalDateTime.parse(dateStr + " " + timeStr, dateTimeFormatter);
                    Instant timestamp = localDateTime.atZone(eastern).toInstant();

                    // Map event type
                    EventType eventType;
                    if ("Arrived".equalsIgnoreCase(csvEventType)) {
                        eventType = EventType.ENTER;
                    } else if ("Departed".equalsIgnoreCase(csvEventType)) {
                        eventType = EventType.EXIT;
                    } else {
                        errors.add(BulkUploadResponse.RowError.builder()
                                .row(rowNum).line(trimmed)
                                .reason("Unknown eventType '" + csvEventType + "'; expected 'Arrived' or 'Departed'").build());
                        continue;
                    }

                    // Resolve zone
                    Zone zone = resolvedZones.get(csvZoneName);
                    if (zone == null) {
                        errors.add(BulkUploadResponse.RowError.builder()
                                .row(rowNum).line(trimmed)
                                .reason("No zone mapping provided for '" + csvZoneName + "'").build());
                        continue;
                    }

                    ZoneEvent event = ZoneEvent.builder()
                            .user(user)
                            .zone(zone)
                            .eventType(eventType)
                            .timestamp(timestamp)
                            .build();
                    eventsToSave.add(event);

                    LocalDate eventDate = localDateTime.toLocalDate();
                    affectedDates.add(eventDate);

                    if (minTimestamp == null || timestamp.isBefore(minTimestamp)) minTimestamp = timestamp;
                    if (maxTimestamp == null || timestamp.isAfter(maxTimestamp)) maxTimestamp = timestamp;

                } catch (DateTimeParseException e) {
                    errors.add(BulkUploadResponse.RowError.builder()
                            .row(rowNum).line(trimmed)
                            .reason("Failed to parse date/time: " + e.getMessage()).build());
                }
            }
        } catch (java.io.IOException e) {
            log.error("Bulk import failed: unable to read CSV file for userId={}, fileName={}", userId, file.getOriginalFilename(), e);
            throw new BusinessRuleException("Failed to read CSV file: " + e.getMessage());
        }

        // If parse errors exist, return without saving
        if (!errors.isEmpty()) {
            log.warn("Bulk import aborted: {} parse errors out of {} rows for userId={}",
                    errors.size(), totalRows, userId);
            return BulkUploadResponse.builder()
                    .totalRows(totalRows).importedCount(0).skippedCount(0)
                    .errors(errors).build();
        }

        if (eventsToSave.isEmpty()) {
            return BulkUploadResponse.builder()
                    .totalRows(totalRows).importedCount(0).skippedCount(0)
                    .errors(List.of()).build();
        }

        // Idempotency: fetch existing events in the date range and skip duplicates
        List<ZoneEvent> existingEvents = eventRepository.findByUserIdAndTimestampRange(
                userId, minTimestamp, maxTimestamp.plusSeconds(1));
        Set<String> existingKeys = new HashSet<>();
        for (ZoneEvent e : existingEvents) {
            existingKeys.add(e.getZone().getId() + "|" + e.getEventType() + "|" + e.getTimestamp());
        }

        List<ZoneEvent> dedupedEvents = new ArrayList<>();
        int skippedCount = 0;
        for (ZoneEvent event : eventsToSave) {
            String key = event.getZone().getId() + "|" + event.getEventType() + "|" + event.getTimestamp();
            if (existingKeys.contains(key)) {
                skippedCount++;
            } else {
                dedupedEvents.add(event);
            }
        }

        // GPS bounce-debounce: drop opposite-direction events within the configured window.
        // Walk events per-zone in timestamp order, seeded with the most recent existing DB
        // event so bounces that straddle the import boundary are caught too.
        dedupedEvents.sort(Comparator.comparing(ZoneEvent::getTimestamp));
        Map<UUID, ZoneEvent> latestByZone = new HashMap<>();
        List<ZoneEvent> newEvents = new ArrayList<>();
        int bouncedCount = 0;
        for (ZoneEvent event : dedupedEvents) {
            UUID zoneId = event.getZone().getId();
            ZoneEvent prior = latestByZone.get(zoneId);
            if (prior == null) {
                prior = eventRepository.findLatestForUserAndZone(userId, zoneId, event.getTimestamp())
                        .orElse(null);
            }
            if (prior != null
                    && prior.getEventType() != event.getEventType()
                    && Math.abs(Duration.between(prior.getTimestamp(), event.getTimestamp()).getSeconds())
                            <= bounceDebounceSeconds) {
                log.warn("Bulk import bounce-debounced: userId={}, zoneId={}, incomingType={}, incomingTs={}, " +
                                "priorType={}, priorTs={}",
                        userId, zoneId, event.getEventType(), event.getTimestamp(),
                        prior.getEventType(), prior.getTimestamp());
                bouncedCount++;
                eventsBounceDebouncedCounter.increment();
                continue;
            }
            newEvents.add(event);
            latestByZone.put(zoneId, event);
        }
        skippedCount += bouncedCount;

        if (!newEvents.isEmpty()) {
            eventRepository.saveAll(newEvents);
            eventsRecordedCounter.increment(newEvents.size());

            // Invalidate OfficeDayRecords for affected dates
            for (LocalDate date : affectedDates) {
                officeDayRecordRepository.deleteByUserIdAndDate(userId, date);
            }
            log.info("OfficeDayRecord cache invalidated for {} dates, userId={}", affectedDates.size(), userId);
        }

        log.info("Bulk import complete: userId={}, total={}, imported={}, skipped={}",
                userId, totalRows, newEvents.size(), skippedCount);

        return BulkUploadResponse.builder()
                .totalRows(totalRows)
                .importedCount(newEvents.size())
                .skippedCount(skippedCount)
                .errors(List.of())
                .build();
    }

    @Transactional
    public void deleteEvent(UUID userId, User user, UUID eventId) {
        ZoneEvent event = eventRepository.findById(eventId)
                .filter(e -> e.getUser().getId().equals(userId))
                .filter(e -> e.getDeletedAt() == null)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventId));

        event.setDeletedAt(Instant.now());
        eventRepository.save(event);

        ZoneId userZone = ZoneId.of(user.getTimezone());
        LocalDate eventDate = event.getTimestamp().atZone(userZone).toLocalDate();
        officeDayRecordRepository.deleteByUserIdAndDate(userId, eventDate);

        log.info("Event soft-deleted: id={}, userId={}, originalDate={}, originalType={}, originalTs={}",
                eventId, userId, eventDate, event.getEventType(), event.getTimestamp());
    }

    private Zone resolveZone(UUID userId, CreateEventRequest request) {
        if (request.getZoneId() != null) {
            return zoneRepository.findByUserIdAndId(userId, request.getZoneId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Zone not found: " + request.getZoneId()));
        }
        if (request.getExternalId() != null) {
            log.debug("Resolving zone by externalId: {}", request.getExternalId());
            return zoneRepository.findByUserIdAndExternalId(userId, request.getExternalId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "externalId '" + request.getExternalId() + "' not found for this user"));
        }
        throw new IllegalArgumentException("Either zoneId or externalId must be provided");
    }
}
