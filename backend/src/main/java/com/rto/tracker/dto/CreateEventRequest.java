package com.rto.tracker.dto;

import com.rto.tracker.domain.EventType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class CreateEventRequest {

    private UUID zoneId;

    @Size(max = 255, message = "externalId must be at most 255 characters")
    private String externalId;

    @NotNull(message = "eventType is required (valid values: ENTER, EXIT)")
    private EventType eventType;

    @NotNull(message = "timestamp is required and must be a valid ISO-8601 instant")
    private Instant timestamp;

    private BigDecimal latitude;
    private BigDecimal longitude;
}
