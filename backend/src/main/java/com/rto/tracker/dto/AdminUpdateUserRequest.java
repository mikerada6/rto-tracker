package com.rto.tracker.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUpdateUserRequest {

    @Size(max = 255, message = "displayName must be at most 255 characters")
    private String displayName;

    @DecimalMin(value = "0.5", message = "requiredDaysPerWeek must be at least 0.5")
    @DecimalMax(value = "5.0", message = "requiredDaysPerWeek must be at most 5.0")
    private BigDecimal requiredDaysPerWeek;
}
