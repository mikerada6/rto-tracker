package com.rto.tracker.config;

import com.rto.tracker.domain.User;
import com.rto.tracker.repository.UserRepository;
import com.rto.tracker.service.UserService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthFilterTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private ApiKeyAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthFilter(userRepository, new SimpleMeterRegistry());
        SecurityContextHolder.clearContext();
    }

    @Test
    void validApiKey_authenticatesUser() throws Exception {
        String apiKey = "test-api-key-12345678901234567890";
        String hash = UserService.hashApiKey(apiKey);

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .displayName("Test")
                .apiKeyHash(hash)
                .active(true)
                .build();

        when(request.getHeader("X-API-Key")).thenReturn(apiKey);
        when(userRepository.findByApiKeyHashAndActiveTrue(hash)).thenReturn(Optional.of(user));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(user);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void invalidApiKey_doesNotAuthenticate() throws Exception {
        String apiKey = "invalid-key";
        String hash = UserService.hashApiKey(apiKey);

        when(request.getHeader("X-API-Key")).thenReturn(apiKey);
        when(userRepository.findByApiKeyHashAndActiveTrue(hash)).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void missingApiKey_doesNotAuthenticate() throws Exception {
        when(request.getHeader("X-API-Key")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
