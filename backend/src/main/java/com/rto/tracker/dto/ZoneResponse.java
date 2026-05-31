package com.rto.tracker.dto;

import com.rto.tracker.domain.Zone;
import com.rto.tracker.domain.ZoneType;
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
public class ZoneResponse {

    private UUID id;
    private String name;
    private ZoneType type;
    private String externalId;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer radiusMeters;
    private boolean active;
    private Instant createdAt;

    public static ZoneResponse from(Zone zone) {
        return ZoneResponse.builder()
                .id(zone.getId())
                .name(zone.getName())
                .type(zone.getType())
                .externalId(zone.getExternalId())
                .latitude(zone.getLatitude())
                .longitude(zone.getLongitude())
                .radiusMeters(zone.getRadiusMeters())
                .active(zone.isActive())
                .createdAt(zone.getCreatedAt())
                .build();
    }
}
