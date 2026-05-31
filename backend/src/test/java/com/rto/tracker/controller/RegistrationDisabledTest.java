package com.rto.tracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rto.tracker.domain.InviteCode;
import com.rto.tracker.domain.User;
import com.rto.tracker.domain.UserRole;
import com.rto.tracker.dto.RegisterRequest;
import com.rto.tracker.repository.InviteCodeRepository;
import com.rto.tracker.repository.UserRepository;
import com.rto.tracker.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.registration-enabled=false")
class RegistrationDisabledTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private InviteCodeRepository inviteCodeRepository;

    @BeforeEach
    void setUp() {
        inviteCodeRepository.deleteAll();
        userRepository.deleteAll();

        User admin = userRepository.save(User.builder()
                .email("admin@test.com")
                .displayName("Admin")
                .apiKeyHash(UserService.hashApiKey("admin-key"))
                .role(UserRole.ADMIN)
                .build());

        inviteCodeRepository.save(InviteCode.builder()
                .code("rto-validcode1234567")
                .createdBy(admin)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build());
    }

    @Test
    void registrationDisabled_returns422() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("new@test.com")
                .displayName("New User")
                .inviteCode("rto-validcode1234567")
                .build();

        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Registration is currently disabled"));
    }
}
