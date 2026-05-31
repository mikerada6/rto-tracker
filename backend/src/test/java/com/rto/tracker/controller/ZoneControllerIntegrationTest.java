package com.rto.tracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rto.tracker.domain.User;
import com.rto.tracker.domain.ZoneType;
import com.rto.tracker.dto.CreateZoneRequest;
import com.rto.tracker.dto.UpdateZoneRequest;
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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ZoneControllerIntegrationTest {

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

    @BeforeEach
    void setUp() {
        officeDayRecordRepository.deleteAll();
        eventRepository.deleteAll();
        zoneRepository.deleteAll();
        userRepository.deleteAll();

        apiKey = UserService.generateApiKey();
        String hash = UserService.hashApiKey(apiKey);

        testUser = userRepository.save(User.builder()
                .email("zone-test@test.com")
                .displayName("Zone Test User")
                .apiKeyHash(hash)
                .build());
    }

    @Test
    void createZone_success() throws Exception {
        CreateZoneRequest request = CreateZoneRequest.builder()
                .name("City Office")
                .type(ZoneType.OFFICE)
                .externalId("zone.city_office")
                .latitude(new BigDecimal("51.507400"))
                .longitude(new BigDecimal("-0.127800"))
                .radiusMeters(200)
                .build();

        mockMvc.perform(post("/api/v1/zones")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("City Office"))
                .andExpect(jsonPath("$.type").value("OFFICE"))
                .andExpect(jsonPath("$.externalId").value("zone.city_office"));
    }

    @Test
    void listZones_filterByType() throws Exception {
        // Create two zones of different types
        createZoneViaApi("Home", ZoneType.HOME, "zone.home");
        createZoneViaApi("Office", ZoneType.OFFICE, "zone.office");

        mockMvc.perform(get("/api/v1/zones")
                        .header("X-API-Key", apiKey)
                        .param("type", "OFFICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("OFFICE"));
    }

    @Test
    void deleteZone_softDeletes() throws Exception {
        MvcResult result = createZoneViaApi("Office", ZoneType.OFFICE, "zone.office");
        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(delete("/api/v1/zones/" + id)
                        .header("X-API-Key", apiKey))
                .andExpect(status().isNoContent());

        // Should not appear in active list
        mockMvc.perform(get("/api/v1/zones")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Should appear in inactive list
        mockMvc.perform(get("/api/v1/zones")
                        .header("X-API-Key", apiKey)
                        .param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void updateZone_success() throws Exception {
        MvcResult result = createZoneViaApi("Old Name", ZoneType.OFFICE, "zone.office");
        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        UpdateZoneRequest update = UpdateZoneRequest.builder()
                .name("New Name")
                .build();

        mockMvc.perform(put("/api/v1/zones/" + id)
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"));
    }

    @Test
    void otherUserCannotAccessZone() throws Exception {
        MvcResult result = createZoneViaApi("Office", ZoneType.OFFICE, "zone.office");
        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        // Create another user
        String otherKey = UserService.generateApiKey();
        userRepository.save(User.builder()
                .email("other@test.com")
                .displayName("Other User")
                .apiKeyHash(UserService.hashApiKey(otherKey))
                .build());

        // Other user cannot update this zone
        mockMvc.perform(put("/api/v1/zones/" + id)
                        .header("X-API-Key", otherKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateZoneRequest.builder().name("Hacked").build())))
                .andExpect(status().isNotFound());
    }

    private MvcResult createZoneViaApi(String name, ZoneType type, String externalId) throws Exception {
        CreateZoneRequest request = CreateZoneRequest.builder()
                .name(name)
                .type(type)
                .externalId(externalId)
                .build();

        return mockMvc.perform(post("/api/v1/zones")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
    }
}
