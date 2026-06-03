package com.rto.tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DayResponse {

    private LocalDate date;
    private boolean officeDay;
    private List<String> officesVisited;
    private String totalOfficeTime;
    private Instant firstOfficeEntry;
    private Instant lastOfficeExit;
    private String commuteDuration;
    private String outboundCommute;
    private String inboundCommute;
    private String commuteRoute;
    private List<DayEventEntry> events;
    private List<CommuteAnnotationDto.Response> commuteAnnotations;
    private Integer anomalyThresholdMinutes;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DayEventEntry {
        private String zone;
        private String zoneType;
        private String type;
        private Instant timestamp;
    }
}
