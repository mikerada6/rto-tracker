package com.rto.tracker.dto;

import com.rto.tracker.domain.User;
import com.rto.tracker.domain.UserRole;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUserResponse {

    private UUID id;
    private String email;
    private String displayName;
    private boolean active;
    private UserRole role;
    private BigDecimal requiredDaysPerWeek;
    private Instant createdAt;

    public static AdminUserResponse from(User user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .active(user.isActive())
                .role(user.getRole())
                .requiredDaysPerWeek(user.getRequiredDaysPerWeek())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
