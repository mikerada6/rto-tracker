package com.rto.tracker.controller;

import com.rto.tracker.domain.EventType;
import com.rto.tracker.domain.User;
import com.rto.tracker.domain.Zone;
import com.rto.tracker.domain.ZoneEvent;
import com.rto.tracker.domain.ZoneType;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportExportControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private ZoneEventRepository eventRepository;
    @Autowired private OfficeDayRecordRepository officeDayRecordRepository;

    private String apiKey;
    private User user;
    private Zone office;

    @BeforeEach
    void setUp() {
        officeDayRecordRepository.deleteAll();
        eventRepository.deleteAll();
        zoneRepository.deleteAll();
        userRepository.deleteAll();

        apiKey = UserService.generateApiKey();
        user = userRepository.save(User.builder()
                .email("report-export@test.com")
                .displayName("Report Tester")
                .apiKeyHash(UserService.hashApiKey(apiKey))
                .build());
        office = zoneRepository.save(Zone.builder()
                .user(user).name("HQ Office").type(ZoneType.OFFICE)
                .externalId("zone.hq").build());

        // Seed one office entry today so the report has content
        eventRepository.save(ZoneEvent.builder()
                .user(user).zone(office).eventType(EventType.ENTER)
                .timestamp(Instant.now()).build());
    }

    @Test
    void exportPdf_requiresApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/reports/export/pdf").param("period", "WEEK"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exportPdf_returnsPdfBytes() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/reports/export/pdf")
                        .param("period", "WEEK")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).isNotEmpty();
        assertThat(body.length).isGreaterThan(1000); // sanity: real PDFs are larger than this
        // PDF magic header
        assertThat(new String(body, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void exportPdf_customWithoutDatesReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/reports/export/pdf")
                        .param("period", "CUSTOM")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportPdf_customWithReversedDatesReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/reports/export/pdf")
                        .param("period", "CUSTOM")
                        .param("from", "2026-06-30")
                        .param("to", "2026-06-01")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportPdf_customOver365DaysReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/reports/export/pdf")
                        .param("period", "CUSTOM")
                        .param("from", "2025-01-01")
                        .param("to", "2026-01-02")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportPdf_unknownPeriodValueReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/reports/export/pdf")
                        .param("period", "DECADE")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportPdf_acceptsAnchorParam() throws Exception {
        // Anchor in the past: produces a fully completed quarter report
        mockMvc.perform(get("/api/v1/reports/export/pdf")
                        .param("period", "QUARTER")
                        .param("anchor", "2025-04-15")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("quarter-2025-04-15")));
    }

    @Test
    void exportPdf_emptyRangeStillSucceeds() throws Exception {
        eventRepository.deleteAll();
        LocalDate today = LocalDate.now();
        mockMvc.perform(get("/api/v1/reports/export/pdf")
                        .param("period", "CUSTOM")
                        .param("from", today.toString())
                        .param("to", today.toString())
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
    }
}
