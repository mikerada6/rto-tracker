package com.rto.tracker.controller;

import com.rto.tracker.domain.CommuteAnnotation;
import com.rto.tracker.domain.OfficeDayRecord;
import com.rto.tracker.domain.User;
import com.rto.tracker.domain.Zone;
import com.rto.tracker.domain.ZoneEvent;
import com.rto.tracker.dto.CommuteAnnotationDto;
import com.rto.tracker.dto.DayResponse;
import com.rto.tracker.dto.ErrorResponse;
import com.rto.tracker.repository.ZoneEventRepository;
import com.rto.tracker.service.CommuteAnnotationService;
import com.rto.tracker.service.DurationFormatter;
import com.rto.tracker.service.OfficeDayCalculationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/v1/days")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Days", description = "Daily office day breakdown")
public class DayController {

    private final OfficeDayCalculationService calculationService;
    private final ZoneEventRepository eventRepository;
    private final CommuteAnnotationService annotationService;

    @GetMapping("/{date}")
    @Operation(summary = "Get day breakdown",
            description = "Returns detailed breakdown for a specific date: office visits, " +
                    "time calculations, commute route, and all zone events.")
    @ApiResponse(responseCode = "200", description = "Day breakdown retrieved")
    @ApiResponse(responseCode = "400", description = "Invalid date format",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid API key")
    public ResponseEntity<DayResponse> getDayBreakdown(
            @AuthenticationPrincipal User user,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        log.info("Day breakdown requested: userId={}, date={}", user.getId(), date);
        OfficeDayRecord record = calculationService.getOrCompute(user.getId(), user, date);

        Instant dayStart = date.atStartOfDay(ZoneId.of(user.getTimezone())).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(ZoneId.of(user.getTimezone())).toInstant();
        List<ZoneEvent> events = eventRepository.findByUserIdAndTimestampRange(user.getId(), dayStart, dayEnd);
        List<CommuteAnnotation> annotations = annotationService.listForDay(user.getId(), date);

        List<String> commuteRoute = calculationService.buildCommuteRoute(events);
        String routeString = commuteRoute.isEmpty() ? null : String.join(" \u2192 ", commuteRoute);

        long outboundSecs = calculationService.calculateOutboundCommuteDuration(events, annotations);
        long inboundSecs = calculationService.calculateInboundCommuteDuration(events, annotations);

        List<DayResponse.DayEventEntry> eventEntries = events.stream()
                .sorted(Comparator.comparing(ZoneEvent::getTimestamp))
                .map(e -> DayResponse.DayEventEntry.builder()
                        .zone(e.getZone().getName())
                        .zoneType(e.getZone().getType().name())
                        .type(e.getEventType().name())
                        .timestamp(e.getTimestamp())
                        .build())
                .toList();

        List<CommuteAnnotationDto.Response> annotationDtos = annotations.stream()
                .map(CommuteAnnotationDto.Response::from)
                .toList();

        DayResponse response = DayResponse.builder()
                .date(date)
                .officeDay(!record.getOfficesVisited().isEmpty())
                .officesVisited(record.getOfficesVisited().stream()
                        .map(Zone::getName)
                        .sorted()
                        .toList())
                .totalOfficeTime(DurationFormatter.format(
                        record.getTotalOfficeTime() != null ? record.getTotalOfficeTime() : 0))
                .firstOfficeEntry(record.getFirstOfficeEntry())
                .lastOfficeExit(record.getLastOfficeExit())
                .commuteDuration(DurationFormatter.format(
                        record.getCommuteDuration() != null ? record.getCommuteDuration() : 0))
                .outboundCommute(outboundSecs > 0 ? DurationFormatter.format(outboundSecs) : null)
                .inboundCommute(inboundSecs > 0 ? DurationFormatter.format(inboundSecs) : null)
                .commuteRoute(routeString)
                .events(eventEntries)
                .commuteAnnotations(annotationDtos)
                .anomalyThresholdMinutes(user.getCommuteAnomalyThresholdMinutes())
                .build();

        log.info("Day breakdown returned: userId={}, date={}, officeDay={}, events={}", user.getId(), date, !record.getOfficesVisited().isEmpty(), eventEntries.size());
        return ResponseEntity.ok(response);
    }
}
