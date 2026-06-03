package com.rto.tracker.service;

import com.rto.tracker.domain.*;
import com.rto.tracker.repository.CommuteAnnotationRepository;
import com.rto.tracker.repository.OfficeDayRecordRepository;
import com.rto.tracker.repository.ZoneEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OfficeDayCalculationServiceTest {

    @Mock
    private ZoneEventRepository eventRepository;

    @Mock
    private OfficeDayRecordRepository recordRepository;

    @Mock
    private CommuteAnnotationRepository annotationRepository;

    private OfficeDayCalculationService service;

    private User testUser;
    private Zone homeZone;
    private Zone officeZone;
    private Zone officeZone2;
    private Zone trainZone;
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        service = new OfficeDayCalculationService(eventRepository, recordRepository, annotationRepository, new SimpleMeterRegistry());
        lenient().when(annotationRepository.findByUserIdAndDateOrderByStartTimeAsc(any(), any()))
                .thenReturn(Collections.emptyList());
        testDate = LocalDate.of(2026, 5, 29);

        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .displayName("Test User")
                .apiKeyHash("hash")
                .build();

        homeZone = Zone.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .name("Home")
                .type(ZoneType.HOME)
                .build();

        officeZone = Zone.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .name("City Office")
                .type(ZoneType.OFFICE)
                .build();

        officeZone2 = Zone.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .name("Local Office")
                .type(ZoneType.OFFICE)
                .build();

        trainZone = Zone.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .name("Train Station A")
                .type(ZoneType.TRAIN_STATION)
                .build();
    }

    private ZoneEvent makeEvent(Zone zone, EventType type, int hour, int minute) {
        ZoneId userZone = ZoneId.of(testUser.getTimezone());
        Instant ts = testDate.atTime(hour, minute).atZone(userZone).toInstant();
        return ZoneEvent.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .zone(zone)
                .eventType(type)
                .timestamp(ts)
                .build();
    }

    // --- Office Day Detection ---

    @Test
    void singleOfficeEnter_noExit_isOfficeDay() {
        List<ZoneEvent> events = List.of(
                makeEvent(officeZone, EventType.ENTER, 9, 0));
        stubEvents(events);

        OfficeDayRecord record = service.compute(testUser.getId(), testUser, testDate);

        assertThat(record.getOfficesVisited()).containsExactly(officeZone);
        // Office time: 9:00 to 23:59:59 = 53999 seconds
        assertThat(record.getTotalOfficeTime()).isEqualTo(53999L);
    }

    @Test
    void singleOfficeEnterAndExit_correctDuration() {
        List<ZoneEvent> events = List.of(
                makeEvent(officeZone, EventType.ENTER, 9, 0),
                makeEvent(officeZone, EventType.EXIT, 17, 30));
        stubEvents(events);

        OfficeDayRecord record = service.compute(testUser.getId(), testUser, testDate);

        assertThat(record.getOfficesVisited()).containsExactly(officeZone);
        // 8h 30m = 30600 seconds
        assertThat(record.getTotalOfficeTime()).isEqualTo(30600L);
        assertThat(record.getFirstOfficeEntry()).isEqualTo(
                testDate.atTime(9, 0).atZone(ZoneId.of(testUser.getTimezone())).toInstant());
        assertThat(record.getLastOfficeExit()).isEqualTo(
                testDate.atTime(17, 30).atZone(ZoneId.of(testUser.getTimezone())).toInstant());
    }

    @Test
    void multipleEnterExitPairs_sameZone_durationsSummed() {
        List<ZoneEvent> events = List.of(
                makeEvent(officeZone, EventType.ENTER, 9, 0),
                makeEvent(officeZone, EventType.EXIT, 12, 0),
                makeEvent(officeZone, EventType.ENTER, 13, 0),
                makeEvent(officeZone, EventType.EXIT, 17, 0));
        stubEvents(events);

        OfficeDayRecord record = service.compute(testUser.getId(), testUser, testDate);

        // 3h + 4h = 7h = 25200 seconds
        assertThat(record.getTotalOfficeTime()).isEqualTo(25200L);
    }

    @Test
    void twoDifferentOfficeZones_bothVisited_timesSummed() {
        List<ZoneEvent> events = List.of(
                makeEvent(officeZone, EventType.ENTER, 9, 0),
                makeEvent(officeZone, EventType.EXIT, 12, 0),
                makeEvent(officeZone2, EventType.ENTER, 14, 0),
                makeEvent(officeZone2, EventType.EXIT, 17, 0));
        stubEvents(events);

        OfficeDayRecord record = service.compute(testUser.getId(), testUser, testDate);

        assertThat(record.getOfficesVisited()).containsExactlyInAnyOrder(officeZone, officeZone2);
        // 3h + 3h = 6h = 21600 seconds
        assertThat(record.getTotalOfficeTime()).isEqualTo(21600L);
    }

    @Test
    void officeExitWithNoPriorEnter_ignored() {
        List<ZoneEvent> events = List.of(
                makeEvent(officeZone, EventType.EXIT, 9, 0));
        stubEvents(events);

        OfficeDayRecord record = service.compute(testUser.getId(), testUser, testDate);

        assertThat(record.getOfficesVisited()).isEmpty();
        assertThat(record.getTotalOfficeTime()).isEqualTo(0L);
    }

    @Test
    void noEvents_notOfficeDay() {
        stubEvents(Collections.emptyList());

        OfficeDayRecord record = service.compute(testUser.getId(), testUser, testDate);

        assertThat(record.getOfficesVisited()).isEmpty();
        assertThat(record.getTotalOfficeTime()).isEqualTo(0L);
        assertThat(record.getCommuteDuration()).isEqualTo(0L);
        assertThat(record.getFirstOfficeEntry()).isNull();
        assertThat(record.getLastOfficeExit()).isNull();
    }

    // --- Commute Duration ---

    @Test
    void fullCommute_morningAndEvening_calculated() {
        List<ZoneEvent> events = List.of(
                makeEvent(homeZone, EventType.EXIT, 7, 55),
                makeEvent(officeZone, EventType.ENTER, 9, 5),
                makeEvent(officeZone, EventType.EXIT, 17, 35),
                makeEvent(homeZone, EventType.ENTER, 18, 15));
        stubEvents(events);

        OfficeDayRecord record = service.compute(testUser.getId(), testUser, testDate);

        // Morning: 7:55 -> 9:05 = 70 min = 4200s
        // Evening: 17:35 -> 18:15 = 40 min = 2400s
        // Total: 110 min = 6600s
        assertThat(record.getCommuteDuration()).isEqualTo(6600L);
    }

    @Test
    void onlyMorningCommute_noHomeReturn_onlyMorningCounted() {
        List<ZoneEvent> events = List.of(
                makeEvent(homeZone, EventType.EXIT, 7, 55),
                makeEvent(officeZone, EventType.ENTER, 9, 5),
                makeEvent(officeZone, EventType.EXIT, 17, 35));
        stubEvents(events);

        OfficeDayRecord record = service.compute(testUser.getId(), testUser, testDate);

        // Only morning: 70 min = 4200s
        assertThat(record.getCommuteDuration()).isEqualTo(4200L);
    }

    @Test
    void noHomeEvents_noCommute() {
        List<ZoneEvent> events = List.of(
                makeEvent(officeZone, EventType.ENTER, 9, 0),
                makeEvent(officeZone, EventType.EXIT, 17, 0));
        stubEvents(events);

        OfficeDayRecord record = service.compute(testUser.getId(), testUser, testDate);

        assertThat(record.getCommuteDuration()).isEqualTo(0L);
    }

    // --- Commute Annotation Subtraction ---

    @Test
    void annotation_fullyInsideInboundWindow_subtracted() {
        List<ZoneEvent> events = List.of(
                makeEvent(homeZone, EventType.EXIT, 7, 15),
                makeEvent(officeZone, EventType.ENTER, 8, 21),
                makeEvent(officeZone, EventType.EXIT, 17, 13),
                makeEvent(homeZone, EventType.ENTER, 21, 19));
        stubEvents(events);

        ZoneId tz = ZoneId.of(testUser.getTimezone());
        CommuteAnnotation happyHour = CommuteAnnotation.builder()
                .startTime(testDate.atTime(17, 13).atZone(tz).toInstant())
                .endTime(testDate.atTime(20, 11).atZone(tz).toInstant())
                .category(CommuteAnnotationCategory.SOCIAL)
                .date(testDate)
                .build();
        when(annotationRepository.findByUserIdAndDateOrderByStartTimeAsc(testUser.getId(), testDate))
                .thenReturn(List.of(happyHour));

        OfficeDayRecord record = service.compute(testUser.getId(), testUser, testDate);

        // Outbound: 07:15 -> 08:21 = 66 min = 3960s
        // Inbound raw: 17:13 -> 21:19 = 4h 6m = 14760s
        // Annotation: 17:13 -> 20:11 = 2h 58m = 10680s
        // Inbound corrected: 14760 - 10680 = 4080s
        // Total: 3960 + 4080 = 8040s
        assertThat(record.getCommuteDuration()).isEqualTo(8040L);
    }

    @Test
    void annotation_straddlingOfficeExit_clippedToWindow() {
        List<ZoneEvent> events = List.of(
                makeEvent(officeZone, EventType.ENTER, 9, 0),
                makeEvent(officeZone, EventType.EXIT, 17, 0),
                makeEvent(homeZone, EventType.ENTER, 19, 0));
        stubEvents(events);

        ZoneId tz = ZoneId.of(testUser.getTimezone());
        // Annotation begins 30 min before office exit and ends 30 min after.
        CommuteAnnotation ann = CommuteAnnotation.builder()
                .startTime(testDate.atTime(16, 30).atZone(tz).toInstant())
                .endTime(testDate.atTime(17, 30).atZone(tz).toInstant())
                .category(CommuteAnnotationCategory.OTHER)
                .date(testDate)
                .build();
        when(annotationRepository.findByUserIdAndDateOrderByStartTimeAsc(testUser.getId(), testDate))
                .thenReturn(List.of(ann));

        OfficeDayRecord record = service.compute(testUser.getId(), testUser, testDate);

        // Inbound raw: 17:00 -> 19:00 = 2h = 7200s
        // Only the 30 min after office exit overlaps the inbound window = 1800s
        // Corrected: 7200 - 1800 = 5400s
        assertThat(record.getCommuteDuration()).isEqualTo(5400L);
    }

    @Test
    void annotation_outsideCommuteWindow_noEffect() {
        List<ZoneEvent> events = List.of(
                makeEvent(homeZone, EventType.EXIT, 8, 0),
                makeEvent(officeZone, EventType.ENTER, 9, 0),
                makeEvent(officeZone, EventType.EXIT, 17, 0),
                makeEvent(homeZone, EventType.ENTER, 18, 0));
        stubEvents(events);

        ZoneId tz = ZoneId.of(testUser.getTimezone());
        // Annotation in the middle of the office day — not in any commute window.
        CommuteAnnotation ann = CommuteAnnotation.builder()
                .startTime(testDate.atTime(12, 0).atZone(tz).toInstant())
                .endTime(testDate.atTime(13, 0).atZone(tz).toInstant())
                .category(CommuteAnnotationCategory.OTHER)
                .date(testDate)
                .build();
        when(annotationRepository.findByUserIdAndDateOrderByStartTimeAsc(testUser.getId(), testDate))
                .thenReturn(List.of(ann));

        OfficeDayRecord record = service.compute(testUser.getId(), testUser, testDate);

        // Outbound 1h + inbound 1h = 7200s, no subtraction
        assertThat(record.getCommuteDuration()).isEqualTo(7200L);
    }

    // --- Commute Route ---

    @Test
    void commuteRoute_fullJourney_orderedCorrectly() {
        List<ZoneEvent> events = List.of(
                makeEvent(homeZone, EventType.EXIT, 7, 55),
                makeEvent(trainZone, EventType.ENTER, 8, 20),
                makeEvent(officeZone, EventType.ENTER, 9, 5));

        List<String> route = service.buildCommuteRoute(events);

        assertThat(route).containsExactly("Home", "Train Station A", "City Office");
    }

    @Test
    void commuteRoute_noHomeExit_emptyRoute() {
        List<ZoneEvent> events = List.of(
                makeEvent(officeZone, EventType.ENTER, 9, 5));

        List<String> route = service.buildCommuteRoute(events);

        assertThat(route).isEmpty();
    }

    @Test
    void commuteRoute_noEvents_emptyRoute() {
        List<String> route = service.buildCommuteRoute(Collections.emptyList());
        assertThat(route).isEmpty();
    }

    // --- First Entry / Last Exit ---

    @Test
    void firstEntryLastExit_multipleOfficeVisits() {
        List<ZoneEvent> events = List.of(
                makeEvent(officeZone, EventType.ENTER, 9, 0),
                makeEvent(officeZone, EventType.EXIT, 12, 0),
                makeEvent(officeZone2, EventType.ENTER, 14, 0),
                makeEvent(officeZone2, EventType.EXIT, 18, 0));
        stubEvents(events);

        OfficeDayRecord record = service.compute(testUser.getId(), testUser, testDate);

        assertThat(record.getFirstOfficeEntry()).isEqualTo(
                testDate.atTime(9, 0).atZone(ZoneId.of(testUser.getTimezone())).toInstant());
        assertThat(record.getLastOfficeExit()).isEqualTo(
                testDate.atTime(18, 0).atZone(ZoneId.of(testUser.getTimezone())).toInstant());
    }

    // --- Cache hit/miss ---

    @Test
    void getOrCompute_cacheHit_returnsExisting() {
        OfficeDayRecord cached = OfficeDayRecord.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .date(testDate)
                .totalOfficeTime(30600L)
                .build();

        when(recordRepository.findByUserIdAndDate(testUser.getId(), testDate))
                .thenReturn(Optional.of(cached));

        OfficeDayRecord result = service.getOrCompute(testUser.getId(), testUser, testDate);

        assertThat(result.getId()).isEqualTo(cached.getId());
        verify(eventRepository, never()).findByUserIdAndTimestampRange(any(), any(), any());
    }

    @Test
    void getOrCompute_cacheMiss_computesAndSaves() {
        when(recordRepository.findByUserIdAndDate(testUser.getId(), testDate))
                .thenReturn(Optional.empty());
        stubEvents(List.of(
                makeEvent(officeZone, EventType.ENTER, 9, 0),
                makeEvent(officeZone, EventType.EXIT, 17, 0)));
        when(recordRepository.save(any(OfficeDayRecord.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        OfficeDayRecord result = service.getOrCompute(testUser.getId(), testUser, testDate);

        assertThat(result.getTotalOfficeTime()).isEqualTo(28800L); // 8h
        verify(recordRepository).save(any(OfficeDayRecord.class));
    }

    private void stubEvents(List<ZoneEvent> events) {
        ZoneId userZone = ZoneId.of(testUser.getTimezone());
        Instant dayStart = testDate.atStartOfDay(userZone).toInstant();
        Instant dayEnd = testDate.plusDays(1).atStartOfDay(userZone).toInstant();
        when(eventRepository.findByUserIdAndTimestampRange(testUser.getId(), dayStart, dayEnd))
                .thenReturn(events);
    }
}
