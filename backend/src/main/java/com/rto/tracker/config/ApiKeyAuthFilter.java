package com.rto.tracker.config;

import com.rto.tracker.domain.User;
import com.rto.tracker.repository.UserRepository;
import com.rto.tracker.service.UserService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Slf4j
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final UserRepository userRepository;
    private final Counter authFailureCounter;

    public ApiKeyAuthFilter(UserRepository userRepository, MeterRegistry meterRegistry) {
        this.userRepository = userRepository;
        this.authFailureCounter = Counter.builder("rto.auth.failures")
                .description("Number of authentication failures")
                .register(meterRegistry);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey != null && !apiKey.isBlank()) {
            String hash = UserService.hashApiKey(apiKey);
            Optional<User> userOpt = userRepository.findByApiKeyHashAndActiveTrue(hash);

            if (userOpt.isPresent()) {
                User user = userOpt.get();
                MDC.put("userId", user.getId().toString());

                var authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(user, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else {
                log.error("Authentication failed: invalid API key");
                authFailureCounter.increment();
            }
        }

        filterChain.doFilter(request, response);
    }
}
