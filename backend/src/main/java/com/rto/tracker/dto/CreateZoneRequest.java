package com.rto.tracker.dto;

import com.rto.tracker.domain.ZoneType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreateZoneRequest {

    @NotBlank(message = "name is required")
    @Size(max = 255, message = "name must be at most 255 characters")
    private String name;

    @NotNull(message = "type is required (valid values: HOME, TRAIN_STATION, OFFICE, OTHER)")
    private ZoneType type;

    @Size(max = 255, message = "externalId must be at most 255 characters")
    private String externalId;

    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer radiusMeters;
}
