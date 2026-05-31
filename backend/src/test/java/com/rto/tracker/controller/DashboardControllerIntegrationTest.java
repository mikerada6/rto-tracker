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
class DashboardControllerIntegrationTest {

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
    private Zone homeZone;
    private Zone officeZone;

    @BeforeEach
    void setUp() {
        officeDayRecordRepository.deleteAll();
        eventRepository.deleteAll();
        zoneRepository.deleteAll();
        userRepository.deleteAll();

        apiKey = UserService.generateApiKey();

        testUser = userRepository.save(User.builder()
                .email("dashboard-test@test.com")
                .displayName("Dashboard Test")
                .apiKeyHash(UserService.hashApiKey(apiKey))
                .build());

        homeZone = zoneRepository.save(Zone.builder()
                .user(testUser).name("Home").type(ZoneType.HOME)
                .externalId("zone.home").build());

        officeZone = zoneRepository.save(Zone.builder()
                .user(testUser).name("City Office").type(ZoneType.OFFICE)
                .externalId("zone.city_office").build());
    }

    @Test
    void dashboardSummary_allFourPeriodsPresent() throws Exception {
        // Seed a few office days in the current period
        LocalDate today = LocalDate.now();
        seedOfficeDay(today.minusDays(1));
        seedOfficeDay(today.minusDays(2));

        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOf").exists())
                .andExpect(jsonPath("$.requiredAveragePerWeek").value(3.0))
                .andExpect(jsonPath("$.week").exists())
                .andExpect(jsonPath("$.week.daysInOffice").isNumber())
                .andExpect(jsonPath("$.week.averageDaysPerWeek").isNumber())
                .andExpect(jsonPath("$.week.weeksRemaining").isNumber())
                .andExpect(jsonPath("$.week.daysStillNeeded").isNumber())
                .andExpect(jsonPath("$.month").exists())
                .andExpect(jsonPath("$.month.daysInOffice").isNumber())
                .andExpect(jsonPath("$.quarter").exists())
                .andExpect(jsonPath("$.quarter.daysInOffice").isNumber())
                .andExpect(jsonPath("$.quarter.isCompliant").isBoolean())
                .andExpect(jsonPath("$.year").exists())
                .andExpect(jsonPath("$.year.daysInOffice").isNumber())
                .andExpect(jsonPath("$.recentCommutes").isArray());
    }

    @Test
    void dashboardSummary_noEvents_allZeroes() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.week.daysInOffice").value(0))
                .andExpect(jsonPath("$.month.daysInOffice").value(0))
                .andExpect(jsonPath("$.quarter.daysInOffice").value(0))
                .andExpect(jsonPath("$.year.daysInOffice").value(0));
    }

    @Test
    void currentQuarterReport_returnsData() throws Exception {
        seedOfficeDay(LocalDate.now().minusDays(1));

        mockMvc.perform(get("/api/v1/reports/quarter/current")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").exists())
                .andExpect(jsonPath("$.periodStart").exists())
                .andExpect(jsonPath("$.periodEnd").exists())
                .andExpect(jsonPath("$.daysInOffice").isNumber())
                .andExpect(jsonPath("$.monthlyBreakdown").isArray())
                .andExpect(jsonPath("$.monthlyBreakdown", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void quarterReport_specificQuarter_returnsData() throws Exception {
        int year = LocalDate.now().getYear();
        int quarter = (LocalDate.now().getMonthValue() - 1) / 3 + 1;

        mockMvc.perform(get("/api/v1/reports/quarter/" + year + "/Q" + quarter)
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value(year + "-Q" + quarter));
    }

    @Test
    void recentCommutes_returnsUpTo5() throws Exception {
        // Seed office days with commute data (home exit + office enter)
        for (int i = 1; i <= 7; i++) {
            LocalDate day = LocalDate.now().minusDays(i);
            seedCommuteDay(day);
        }

        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentCommutes", hasSize(5)));
    }

    private void seedOfficeDay(LocalDate date) {
        Instant enter = date.atTime(9, 0).toInstant(ZoneOffset.UTC);
        Instant exit = date.atTime(17, 0).toInstant(ZoneOffset.UTC);
        eventRepository.save(ZoneEvent.builder()
                .user(testUser).zone(officeZone).eventType(EventType.ENTER)
                .timestamp(enter).build());
        eventRepository.save(ZoneEvent.builder()
                .user(testUser).zone(officeZone).eventType(EventType.EXIT)
                .timestamp(exit).build());
    }

    private void seedCommuteDay(LocalDate date) {
        eventRepository.save(ZoneEvent.builder()
                .user(testUser).zone(homeZone).eventType(EventType.EXIT)
                .timestamp(date.atTime(7, 55).toInstant(ZoneOffset.UTC)).build());
        eventRepository.save(ZoneEvent.builder()
                .user(testUser).zone(officeZone).eventType(EventType.ENTER)
                .timestamp(date.atTime(9, 5).toInstant(ZoneOffset.UTC)).build());
        eventRepository.save(ZoneEvent.builder()
                .user(testUser).zone(officeZone).eventType(EventType.EXIT)
                .timestamp(date.atTime(17, 35).toInstant(ZoneOffset.UTC)).build());
        eventRepository.save(ZoneEvent.builder()
                .user(testUser).zone(homeZone).eventType(EventType.ENTER)
                .timestamp(date.atTime(18, 15).toInstant(ZoneOffset.UTC)).build());
    }
}
