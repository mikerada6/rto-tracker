package com.rto.tracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rto.tracker.domain.*;
import com.rto.tracker.dto.CreateEventRequest;
import com.rto.tracker.dto.UpdateUserRequest;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Phase4IntegrationTest {

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
    private Zone testZone;

    @BeforeEach
    void setUp() {
        officeDayRecordRepository.deleteAll();
        eventRepository.deleteAll();
        zoneRepository.deleteAll();
        userRepository.deleteAll();

        apiKey = UserService.generateApiKey();
        String hash = UserService.hashApiKey(apiKey);

        testUser = userRepository.save(User.builder()
                .email("phase4@test.com")
                .displayName("Phase4 Test")
                .apiKeyHash(hash)
                .build());

        testZone = zoneRepository.save(Zone.builder()
                .user(testUser)
                .name("City Office")
                .type(ZoneType.OFFICE)
                .externalId("zone.city_office")
                .build());
    }

    // ── Error Response Shape ──────────────────────────────────────

    @Test
    void validationErrors_returnFieldErrors() throws Exception {
        // Send empty event request to trigger validation
        String body = "{}";

        mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.path").value("/api/v1/events"));
    }

    @Test
    void notFound_returnsStandardShape() throws Exception {
        mockMvc.perform(delete("/api/v1/zones/00000000-0000-0000-0000-000000000001")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void futureTimestamp_returns422() throws Exception {
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
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value(containsString("future")));
    }

    // ── Malformed UUID ────────────────────────────────────────────

    @Test
    void malformedUuid_returns400() throws Exception {
        mockMvc.perform(delete("/api/v1/zones/not-a-uuid")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Invalid UUID format")));
    }

    // ── Unknown Enum ──────────────────────────────────────────────

    @Test
    void unknownEnumValue_returns400() throws Exception {
        String body = """
                {
                    "externalId": "zone.city_office",
                    "eventType": "INVALID_TYPE",
                    "timestamp": "%s"
                }
                """.formatted(Instant.now().minus(1, ChronoUnit.HOURS).toString());

        mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    // ── PATCH /api/v1/users/me ────────────────────────────────────

    @Test
    void patchUser_updatesDisplayName() throws Exception {
        UpdateUserRequest request = UpdateUserRequest.builder()
                .displayName("Updated Name")
                .build();

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Updated Name"));
    }

    @Test
    void patchUser_updatesRequiredDaysPerWeek() throws Exception {
        UpdateUserRequest request = UpdateUserRequest.builder()
                .requiredDaysPerWeek(new BigDecimal("4.0"))
                .build();

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiredDaysPerWeek").value(4.0));
    }

    @Test
    void patchUser_requiredDaysOutOfRange_returns400() throws Exception {
        UpdateUserRequest request = UpdateUserRequest.builder()
                .requiredDaysPerWeek(new BigDecimal("6.0"))
                .build();

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("requiredDaysPerWeek"));
    }

    @Test
    void patchUser_requiredDaysTooLow_returns400() throws Exception {
        UpdateUserRequest request = UpdateUserRequest.builder()
                .requiredDaysPerWeek(new BigDecimal("0.1"))
                .build();

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("requiredDaysPerWeek"));
    }

    // ── API Key Regeneration ──────────────────────────────────────

    @Test
    void regenerateKey_oldKeyRejected() throws Exception {
        // Regenerate
        String response = mockMvc.perform(post("/api/v1/users/me/regenerate-key")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").exists())
                .andReturn().getResponse().getContentAsString();

        // Old key should now be rejected
        mockMvc.perform(get("/api/v1/users/me")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isUnauthorized());

        // New key should work
        String newKey = objectMapper.readTree(response).get("apiKey").asText();
        mockMvc.perform(get("/api/v1/users/me")
                        .header("X-API-Key", newKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("phase4@test.com"));
    }

    // ── Health Endpoint ───────────────────────────────────────────

    @Test
    void healthEndpoint_returnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // ── Size Validation ───────────────────────────────────────────

    @Test
    void createZone_nameTooLong_returns400() throws Exception {
        String longName = "x".repeat(256);
        String body = """
                {
                    "name": "%s",
                    "type": "OFFICE"
                }
                """.formatted(longName);

        mockMvc.perform(post("/api/v1/zones")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }
}
