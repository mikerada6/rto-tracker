package com.rto.tracker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateInviteCodeRequest {

    @Min(value = 1, message = "expiresInDays must be at least 1")
    @Max(value = 90, message = "expiresInDays must be at most 90")
    @Builder.Default
    private int expiresInDays = 7;
}
