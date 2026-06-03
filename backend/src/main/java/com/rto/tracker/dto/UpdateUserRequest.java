package com.rto.tracker.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserRequest {

    @Size(max = 255, message = "displayName must be at most 255 characters")
    private String displayName;

    @DecimalMin(value = "0.5", message = "requiredDaysPerWeek must be at least 0.5")
    @DecimalMax(value = "5.0", message = "requiredDaysPerWeek must be at most 5.0")
    private BigDecimal requiredDaysPerWeek;

    @Size(max = 64, message = "timezone must be at most 64 characters")
    private String timezone;

    @Min(value = 5, message = "commuteAnomalyThresholdMinutes must be at least 5")
    @Max(value = 240, message = "commuteAnomalyThresholdMinutes must be at most 240")
    private Integer commuteAnomalyThresholdMinutes;
}
