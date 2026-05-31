package com.rto.tracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rto.tracker.domain.*;
import com.rto.tracker.dto.CreateEventRequest;
import com.rto.tracker.repository.OfficeDayRecordRepository;
import com.rto.tracker.repository.UserRepository;
import com.rto.tracker.repository.ZoneEventRepository;
import com.rto.tracker.repository.ZoneRepository;
import com.rto.tracker.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DayControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private ZoneEventRepository eventRepository;

    @Autowired
    private OfficeDayRecordRepository officeDayRecordRepository;

    private String apiKey;
    private User testUser;
    private Zone homeZone;
    private Zone officeZone;
    private Zone trainZone;

    @BeforeEach
    void setUp() {
        officeDayRecordRepository.deleteAll();
        eventRepository.deleteAll();
        zoneRepository.deleteAll();
        userRepository.deleteAll();

        apiKey = UserService.generateApiKey();

        testUser = userRepository.save(User.builder()
                .email("day-test@test.com")
                .displayName("Day Test User")
                .apiKeyHash(UserService.hashApiKey(apiKey))
                .build());

        homeZone = zoneRepository.save(Zone.builder()
                .user(testUser).name("Home").type(ZoneType.HOME)
                .externalId("zone.home").build());

        officeZone = zoneRepository.save(Zone.builder()
                .user(testUser).name("City Office").type(ZoneType.OFFICE)
                .externalId("zone.city_office").build());

        trainZone = zoneRepository.save(Zone.builder()
                .user(testUser).name("Train Station A").type(ZoneType.TRAIN_STATION)
                .externalId("zone.train_a").build());
    }

    @Test
    void getDayBreakdown_fullCommute_returnsCorrectData() throws Exception {
        LocalDate date = LocalDate.of(2026, 5, 28);

        // Seed events directly
        createEvent(homeZone, EventType.EXIT, date, 7, 55);
        createEvent(trainZone, EventType.ENTER, date, 8, 20);
        createEvent(officeZone, EventType.ENTER, date, 9, 5);
        createEvent(officeZone, EventType.EXIT, date, 17, 35);
        createEvent(homeZone, EventType.ENTER, date, 18, 15);

        mockMvc.perform(get("/api/v1/days/2026-05-28")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-05-28"))
                .andExpect(jsonPath("$.officeDay").value(true))
                .andExpect(jsonPath("$.officesVisited", hasSize(1)))
                .andExpect(jsonPath("$.officesVisited[0]").value("City Office"))
                .andExpect(jsonPath("$.totalOfficeTime").value("8h 30m"))
                .andExpect(jsonPath("$.commuteDuration").value("1h 50m"))
                .andExpect(jsonPath("$.commuteRoute").value("Home \u2192 Train Station A \u2192 City Office"))
                .andExpect(jsonPath("$.events", hasSize(5)));
    }

    @Test
    void getDayBreakdown_noEvents_returnsEmptyDay() throws Exception {
        mockMvc.perform(get("/api/v1/days/2026-05-20")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-05-20"))
                .andExpect(jsonPath("$.officeDay").value(false))
                .andExpect(jsonPath("$.officesVisited", hasSize(0)))
                .andExpect(jsonPath("$.totalOfficeTime").value("0m"))
                .andExpect(jsonPath("$.commuteDuration").value("0m"))
                .andExpect(jsonPath("$.commuteRoute").isEmpty())
                .andExpect(jsonPath("$.events", hasSize(0)));
    }

    @Test
    void recordNewEvent_invalidatesCachedRecord() throws Exception {
        LocalDate date = LocalDate.of(2026, 5, 27);

        // Create initial event and fetch day to populate cache
        createEvent(officeZone, EventType.ENTER, date, 9, 0);

        mockMvc.perform(get("/api/v1/days/2026-05-27")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.officeDay").value(true));

        // Verify record is cached
        assertThat(officeDayRecordRepository.findByUserIdAndDate(testUser.getId(), date)).isPresent();

        // Record a new event for the same date via API
        Instant exitTime = date.atTime(17, 0).toInstant(ZoneOffset.UTC);
        CreateEventRequest request = CreateEventRequest.builder()
                .externalId("zone.city_office")
                .eventType(EventType.EXIT)
                .timestamp(exitTime)
                .build();

        mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Cache should be invalidated
        assertThat(officeDayRecordRepository.findByUserIdAndDate(testUser.getId(), date)).isEmpty();

        // Next fetch should re-compute with both events
        mockMvc.perform(get("/api/v1/days/2026-05-27")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.officeDay").value(true))
                .andExpect(jsonPath("$.totalOfficeTime").value("8h"));
    }

    @Test
    void getDayBreakdown_futureDate_returnsEmptyDay() throws Exception {
        mockMvc.perform(get("/api/v1/days/2027-01-01")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.officeDay").value(false))
                .andExpect(jsonPath("$.events", hasSize(0)));
    }

    private void createEvent(Zone zone, EventType type, LocalDate date, int hour, int minute) {
        Instant ts = date.atTime(hour, minute).toInstant(ZoneOffset.UTC);
        eventRepository.save(ZoneEvent.builder()
                .user(testUser)
                .zone(zone)
                .eventType(type)
                .timestamp(ts)
                .build());
    }

}
