package com.rto.tracker.dto;

import com.rto.tracker.domain.User;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterResponse {

    private UUID id;
    private String email;
    private String displayName;
    private String apiKey;
    private BigDecimal requiredDaysPerWeek;
    private Instant createdAt;

    public static RegisterResponse from(User user, String plaintextApiKey) {
        return RegisterResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .apiKey(plaintextApiKey)
                .requiredDaysPerWeek(user.getRequiredDaysPerWeek())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
