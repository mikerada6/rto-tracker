package com.rto.tracker.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rto.tracker.domain.EventType;
import com.rto.tracker.domain.User;
import com.rto.tracker.domain.ZoneEvent;
import com.rto.tracker.dto.BulkUploadResponse;
import com.rto.tracker.dto.CreateEventRequest;
import com.rto.tracker.dto.ErrorResponse;
import com.rto.tracker.dto.ZoneEventResponse;
import com.rto.tracker.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Events", description = "Zone event recording and retrieval")
public class EventController {

    private final EventService eventService;
    private final ObjectMapper objectMapper;

    @PostMapping
    @Operation(summary = "Record a zone event",
            description = "Called by Home Assistant automations when entering or leaving a zone. " +
                    "Duplicate events within the deduplication window are returned as-is.")
    @ApiResponse(responseCode = "201", description = "Event recorded")
    @ApiResponse(responseCode = "200", description = "Duplicate event — returned existing record")
    @ApiResponse(responseCode = "400", description = "Validation failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid API key")
    @ApiResponse(responseCode = "422", description = "Business rule violation (e.g. future timestamp)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ZoneEventResponse> recordEvent(@AuthenticationPrincipal User user,
                                                          @Valid @RequestBody CreateEventRequest request) {
        log.info("Recording event: userId={}, eventType={}, timestamp={}", user.getId(), request.getEventType(), request.getTimestamp());
        ZoneEvent event = eventService.recordEvent(user.getId(), user, request);
        log.info("Event recorded: eventId={}, userId={}", event.getId(), user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ZoneEventResponse.from(event));
    }

    @GetMapping
    @Operation(summary = "List zone events",
            description = "Retrieve zone events for a date range with optional zone and event type filters.")
    @ApiResponse(responseCode = "200", description = "Events retrieved")
    @ApiResponse(responseCode = "401", description = "Missing or invalid API key")
    public ResponseEntity<List<ZoneEventResponse>> listEvents(
            @AuthenticationPrincipal User user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) EventType eventType) {
        log.info("Listing events: userId={}, startDate={}, endDate={}, zoneId={}, eventType={}", user.getId(), startDate, endDate, zoneId, eventType);
        List<ZoneEvent> events = eventService.listEvents(user.getId(), user.getTimezone(), startDate, endDate, zoneId, eventType);
        log.info("Listed {} events for userId={}", events.size(), user.getId());
        List<ZoneEventResponse> response = events.stream().map(ZoneEventResponse::from).toList();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{eventId}")
    @Operation(summary = "Delete a zone event",
            description = "Soft-deletes a zone event the user owns. Useful for cleaning up GPS-bounce events " +
                    "or other bad data that slipped past the ingestion-time debounce.")
    @ApiResponse(responseCode = "204", description = "Event deleted")
    @ApiResponse(responseCode = "401", description = "Missing or invalid API key")
    @ApiResponse(responseCode = "404", description = "Event not found or not owned by this user",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Void> deleteEvent(@AuthenticationPrincipal User user,
                                            @PathVariable UUID eventId) {
        log.info("Deleting event: userId={}, eventId={}", user.getId(), eventId);
        eventService.deleteEvent(user.getId(), user, eventId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Bulk import zone events from CSV",
            description = "Uploads a CSV file of historical zone events. The CSV must have columns: " +
                    "Date, Time, externalId, eventType. The zoneMapping parameter is a JSON string mapping " +
                    "CSV zone names to database externalId values, e.g. " +
                    "{\"Home\":\"zone.home\",\"Work\":\"zone.office\"}. " +
                    "The endpoint is idempotent — duplicate events are skipped.")
    @ApiResponse(responseCode = "200", description = "Import completed")
    @ApiResponse(responseCode = "400", description = "Invalid CSV or zone mapping",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid API key")
    public ResponseEntity<BulkUploadResponse> uploadEvents(
            @AuthenticationPrincipal User user,
            @RequestPart("file") MultipartFile file,
            @Schema(description = "JSON mapping of CSV zone names to database externalId values",
                    example = "{\"Home\":\"zone.home\",\"Work\":\"zone.office\"}")
            @RequestParam("zoneMapping") String zoneMappingJson) {
        Map<String, String> zoneMapping;
        try {
            zoneMapping = objectMapper.readValue(zoneMappingJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Bulk upload failed: userId={}, fileName={}, reason=invalid zoneMapping JSON, detail={}",
                    user.getId(), file.getOriginalFilename(), e.getMessage());
            throw new IllegalArgumentException(
                    "Invalid zoneMapping JSON: " + e.getMessage());
        }

        if (file.isEmpty()) {
            log.warn("Bulk upload failed: userId={}, reason=empty file", user.getId());
            throw new IllegalArgumentException("CSV file is empty");
        }

        log.info("Bulk upload started: userId={}, fileName={}, fileSize={} bytes, zoneMappings={}",
                user.getId(), file.getOriginalFilename(), file.getSize(), zoneMapping);
        BulkUploadResponse response = eventService.bulkImport(user, file, zoneMapping);
        log.info("Bulk upload complete: userId={}, imported={}, skipped={}, errors={}",
                user.getId(), response.getImportedCount(), response.getSkippedCount(), response.getErrors().size());
        if (!response.getErrors().isEmpty()) {
            log.warn("Bulk upload had row errors: userId={}, errorCount={}, firstError=row {} - {}",
                    user.getId(), response.getErrors().size(),
                    response.getErrors().getFirst().getRow(), response.getErrors().getFirst().getReason());
        }
        return ResponseEntity.ok(response);
    }
}
