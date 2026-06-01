package com.rto.tracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rto.tracker.domain.*;
import com.rto.tracker.dto.CreateEventRequest;
import com.rto.tracker.repository.InviteCodeRepository;
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
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventControllerIntegrationTest {

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

    @Autowired
    private InviteCodeRepository inviteCodeRepository;

    private String apiKey;
    private User testUser;
    private Zone testZone;

    @BeforeEach
    void setUp() {
        inviteCodeRepository.deleteAll();
        officeDayRecordRepository.deleteAll();
        eventRepository.deleteAll();
        zoneRepository.deleteAll();
        userRepository.deleteAll();

        apiKey = UserService.generateApiKey();
        String hash = UserService.hashApiKey(apiKey);

        testUser = userRepository.save(User.builder()
                .email("integration@test.com")
                .displayName("Integration Test")
                .apiKeyHash(hash)
                .build());

        testZone = zoneRepository.save(Zone.builder()
                .user(testUser)
                .name("City Office")
                .type(ZoneType.OFFICE)
                .externalId("zone.city_office")
                .build());
    }

    @Test
    void recordEvent_success() throws Exception {
        CreateEventRequest request = CreateEventRequest.builder()
                .externalId("zone.city_office")
                .eventType(EventType.ENTER)
                .timestamp(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.zoneName").value("City Office"))
                .andExpect(jsonPath("$.eventType").value("ENTER"));
    }

    @Test
    void recordEvent_futureTimestamp_returns422() throws Exception {
        CreateEventRequest request = CreateEventRequest.builder()
                .externalId("zone.city_office")
                .eventType(EventType.ENTER)
                .timestamp(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("future")));
    }

    @Test
    void recordEvent_noAuth_returns401() throws Exception {
        CreateEventRequest request = CreateEventRequest.builder()
                .externalId("zone.city_office")
                .eventType(EventType.ENTER)
                .timestamp(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recordEvent_duplicateWithinWindow_deduplicates() throws Exception {
        Instant timestamp = Instant.now().minus(1, ChronoUnit.HOURS);

        CreateEventRequest request = CreateEventRequest.builder()
                .externalId("zone.city_office")
                .eventType(EventType.ENTER)
                .timestamp(timestamp)
                .build();

        // First request
        mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Duplicate request (within 5 min window)
        CreateEventRequest duplicate = CreateEventRequest.builder()
                .externalId("zone.city_office")
                .eventType(EventType.ENTER)
                .timestamp(timestamp.plus(2, ChronoUnit.MINUTES))
                .build();

        mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isCreated());

        // Should still only have 1 event
        mockMvc.perform(get("/api/v1/events")
                        .header("X-API-Key", apiKey)
                        .param("startDate", java.time.LocalDate.now().minusDays(1).toString())
                        .param("endDate", java.time.LocalDate.now().plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void listEvents_onlyReturnsAuthenticatedUserEvents() throws Exception {
        // Create event for testUser
        Instant timestamp = Instant.now().minus(1, ChronoUnit.HOURS);
        CreateEventRequest request = CreateEventRequest.builder()
                .externalId("zone.city_office")
                .eventType(EventType.ENTER)
                .timestamp(timestamp)
                .build();

        mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Create another user
        String otherKey = UserService.generateApiKey();
        User otherUser = userRepository.save(User.builder()
                .email("other@test.com")
                .displayName("Other User")
                .apiKeyHash(UserService.hashApiKey(otherKey))
                .build());

        // Other user should see no events
        mockMvc.perform(get("/api/v1/events")
                        .header("X-API-Key", otherKey)
                        .param("startDate", java.time.LocalDate.now().minusDays(1).toString())
                        .param("endDate", java.time.LocalDate.now().plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
