package com.rto.tracker.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {

    @NotBlank(message = "email is required")
    @Email(message = "email must be a valid email address")
    private String email;

    @NotBlank(message = "displayName is required")
    @Size(max = 255, message = "displayName must be at most 255 characters")
    private String displayName;

    @NotBlank(message = "inviteCode is required")
    private String inviteCode;

    @DecimalMin(value = "0.5", message = "requiredDaysPerWeek must be at least 0.5")
    @DecimalMax(value = "5.0", message = "requiredDaysPerWeek must be at most 5.0")
    @Builder.Default
    private BigDecimal requiredDaysPerWeek = new BigDecimal("3.0");
}
