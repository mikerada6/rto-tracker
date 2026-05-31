package com.rto.tracker.dto;

import com.rto.tracker.domain.User;
import com.rto.tracker.domain.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    private UUID id;
    private String email;
    private String displayName;
    private boolean active;
    private UserRole role;
    private BigDecimal requiredDaysPerWeek;
    private String timezone;
    private Instant createdAt;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .active(user.isActive())
                .role(user.getRole())
                .requiredDaysPerWeek(user.getRequiredDaysPerWeek())
                .timezone(user.getTimezone())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
