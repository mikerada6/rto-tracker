package com.rto.tracker.service;

import com.rto.tracker.domain.*;
import com.rto.tracker.dto.CreateEventRequest;
import com.rto.tracker.exception.EntityNotFoundException;
import com.rto.tracker.repository.OfficeDayRecordRepository;
import com.rto.tracker.repository.ZoneEventRepository;
import com.rto.tracker.repository.ZoneRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private ZoneEventRepository eventRepository;

    @Mock
    private ZoneRepository zoneRepository;

    @Mock
    private OfficeDayRecordRepository officeDayRecordRepository;

    private EventService eventService;
    private User testUser;
    private Zone testZone;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventRepository, zoneRepository, officeDayRecordRepository, new SimpleMeterRegistry(), 5);

        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .displayName("Test User")
                .apiKeyHash("hash")
                .build();

        testZone = Zone.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .name("City Office")
                .type(ZoneType.OFFICE)
                .externalId("zone.city_office")
                .build();
    }

    @Test
    void recordEvent_withExternalId_resolvesZoneAndSaves() {
        CreateEventRequest request = CreateEventRequest.builder()
                .externalId("zone.city_office")
                .eventType(EventType.ENTER)
                .timestamp(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        when(zoneRepository.findByUserIdAndExternalId(testUser.getId(), "zone.city_office"))
                .thenReturn(Optional.of(testZone));
        when(eventRepository.findDuplicates(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        ZoneEvent savedEvent = ZoneEvent.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .zone(testZone)
                .eventType(EventType.ENTER)
                .timestamp(request.getTimestamp())
                .build();
        when(eventRepository.save(any(ZoneEvent.class))).thenReturn(savedEvent);

        ZoneEvent result = eventService.recordEvent(testUser.getId(), testUser, request);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getZone()).isEqualTo(testZone);
        verify(eventRepository).save(any(ZoneEvent.class));
    }

    @Test
    void recordEvent_futureTimestamp_throwsException() {
        CreateEventRequest request = CreateEventRequest.builder()
                .externalId("zone.city_office")
                .eventType(EventType.ENTER)
                .timestamp(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        assertThatThrownBy(() -> eventService.recordEvent(testUser.getId(), testUser, request))
                .isInstanceOf(com.rto.tracker.exception.BusinessRuleException.class)
                .hasMessageContaining("future");
    }

    @Test
    void recordEvent_unknownExternalId_throwsEntityNotFound() {
        CreateEventRequest request = CreateEventRequest.builder()
                .externalId("zone.unknown")
                .eventType(EventType.ENTER)
                .timestamp(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        when(zoneRepository.findByUserIdAndExternalId(testUser.getId(), "zone.unknown"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.recordEvent(testUser.getId(), testUser, request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("zone.unknown");
    }

    @Test
    void recordEvent_duplicateWithinWindow_returnExisting() {
        CreateEventRequest request = CreateEventRequest.builder()
                .externalId("zone.city_office")
                .eventType(EventType.ENTER)
                .timestamp(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        ZoneEvent existing = ZoneEvent.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .zone(testZone)
                .eventType(EventType.ENTER)
                .timestamp(request.getTimestamp().minus(2, ChronoUnit.MINUTES))
                .build();

        when(zoneRepository.findByUserIdAndExternalId(testUser.getId(), "zone.city_office"))
                .thenReturn(Optional.of(testZone));
        when(eventRepository.findDuplicates(any(), any(), any(), any(), any()))
                .thenReturn(List.of(existing));

        ZoneEvent result = eventService.recordEvent(testUser.getId(), testUser, request);

        assertThat(result.getId()).isEqualTo(existing.getId());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void recordEvent_noZoneIdOrExternalId_throwsException() {
        CreateEventRequest request = CreateEventRequest.builder()
                .eventType(EventType.ENTER)
                .timestamp(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        assertThatThrownBy(() -> eventService.recordEvent(testUser.getId(), testUser, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zoneId or externalId");
    }
}
