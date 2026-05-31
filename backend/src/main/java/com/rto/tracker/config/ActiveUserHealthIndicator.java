package com.rto.tracker.config;

import com.rto.tracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ActiveUserHealthIndicator implements HealthIndicator {

    private final UserRepository userRepository;

    @Override
    public Health health() {
        long activeUsers = userRepository.countByActiveTrue();
        if (activeUsers == 0) {
            return Health.status("WARN")
                    .withDetail("activeUsers", 0)
                    .withDetail("message", "No active users found. HA automations will fail silently.")
                    .build();
        }
        return Health.up()
                .withDetail("activeUsers", activeUsers)
                .build();
    }
}
