package com.rto.tracker.controller;

import com.rto.tracker.domain.*;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.*;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
    private Zone officeZone;
    private Zone secondOffice;

    @BeforeEach
    void setUp() {
        officeDayRecordRepository.deleteAll();
        eventRepository.deleteAll();
        zoneRepository.deleteAll();
        userRepository.deleteAll();

        apiKey = UserService.generateApiKey();

        testUser = userRepository.save(User.builder()
                .email("audit-test@test.com")
                .displayName("Audit Test")
                .apiKeyHash(UserService.hashApiKey(apiKey))
                .build());

        officeZone = zoneRepository.save(Zone.builder()
                .user(testUser).name("Downtown Office").type(ZoneType.OFFICE)
                .externalId("zone.downtown").build());

        secondOffice = zoneRepository.save(Zone.builder()
                .user(testUser).name("Midtown Office").type(ZoneType.OFFICE)
                .externalId("zone.midtown").build());
    }

    @Test
    void audit_withExplicitDates_returnsOfficeDays() throws Exception {
        LocalDate day1 = LocalDate.now().minusDays(3);
        LocalDate day2 = LocalDate.now().minusDays(1);
        seedOfficeDay(day1, officeZone);
        seedOfficeDay(day2, secondOffice);

        mockMvc.perform(get("/api/v1/audit/office-days")
                        .param("startDate", day1.minusDays(1).toString())
                        .param("endDate", LocalDate.now().toString())
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOfficeDays").value(2))
                .andExpect(jsonPath("$.days", hasSize(2)))
                .andExpect(jsonPath("$.days[0].officesVisited").isArray())
                .andExpect(jsonPath("$.days[0].totalOfficeTime").isString())
                .andExpect(jsonPath("$.days[0].totalOfficeTimeSeconds").isNumber())
                .andExpect(jsonPath("$.days[0].firstOfficeEntry").exists())
                .andExpect(jsonPath("$.days[0].lastOfficeExit").exists());
    }

    @Test
    void audit_defaultsToCurrentQuarter() throws Exception {
        LocalDate today = LocalDate.now();
        int quarterMonth = ((today.getMonthValue() - 1) / 3) * 3 + 1;
        LocalDate quarterStart = LocalDate.of(today.getYear(), quarterMonth, 1);

        seedOfficeDay(today.minusDays(1), officeZone);

        mockMvc.perform(get("/api/v1/audit/office-days")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value(quarterStart.toString()));
    }

    @Test
    void audit_noOfficeDays_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/audit/office-days")
                        .param("startDate", LocalDate.now().minusDays(7).toString())
                        .param("endDate", LocalDate.now().toString())
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOfficeDays").value(0))
                .andExpect(jsonPath("$.days", hasSize(0)));
    }

    @Test
    void audit_invalidDateRange_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/audit/office-days")
                        .param("startDate", "2026-06-01")
                        .param("endDate", "2026-05-01")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isBadRequest());
    }

    @Test
    void audit_noApiKey_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/audit/office-days"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void audit_multipleOffices_showsAllVisited() throws Exception {
        LocalDate day = LocalDate.now().minusDays(1);
        // Visit two offices on the same day
        seedOfficeDay(day, officeZone);
        Instant enter2 = day.atTime(14, 0).toInstant(ZoneOffset.UTC);
        Instant exit2 = day.atTime(16, 0).toInstant(ZoneOffset.UTC);
        eventRepository.save(ZoneEvent.builder()
                .user(testUser).zone(secondOffice).eventType(EventType.ENTER)
                .timestamp(enter2).build());
        eventRepository.save(ZoneEvent.builder()
                .user(testUser).zone(secondOffice).eventType(EventType.EXIT)
                .timestamp(exit2).build());

        mockMvc.perform(get("/api/v1/audit/office-days")
                        .param("startDate", day.toString())
                        .param("endDate", day.toString())
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOfficeDays").value(1))
                .andExpect(jsonPath("$.days[0].officesVisited", hasSize(2)))
                .andExpect(jsonPath("$.days[0].officesVisited", containsInAnyOrder("Downtown Office", "Midtown Office")));
    }

    private void seedOfficeDay(LocalDate date, Zone office) {
        Instant enter = date.atTime(9, 0).toInstant(ZoneOffset.UTC);
        Instant exit = date.atTime(17, 0).toInstant(ZoneOffset.UTC);
        eventRepository.save(ZoneEvent.builder()
                .user(testUser).zone(office).eventType(EventType.ENTER)
                .timestamp(enter).build());
        eventRepository.save(ZoneEvent.builder()
                .user(testUser).zone(office).eventType(EventType.EXIT)
                .timestamp(exit).build());
    }
}
