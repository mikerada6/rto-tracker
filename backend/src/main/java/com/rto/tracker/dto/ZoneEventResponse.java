package com.rto.tracker.dto;

import com.rto.tracker.domain.EventType;
import com.rto.tracker.domain.ZoneEvent;
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
public class ZoneEventResponse {

    private UUID id;
    private UUID zoneId;
    private String zoneName;
    private EventType eventType;
    private Instant timestamp;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Instant createdAt;

    public static ZoneEventResponse from(ZoneEvent event) {
        return ZoneEventResponse.builder()
                .id(event.getId())
                .zoneId(event.getZone().getId())
                .zoneName(event.getZone().getName())
                .eventType(event.getEventType())
                .timestamp(event.getTimestamp())
                .latitude(event.getLatitude())
                .longitude(event.getLongitude())
                .createdAt(event.getCreatedAt())
                .build();
    }
}
