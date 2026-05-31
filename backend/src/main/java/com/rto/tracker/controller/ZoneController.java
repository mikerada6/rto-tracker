package com.rto.tracker.controller;

import com.rto.tracker.domain.User;
import com.rto.tracker.domain.Zone;
import com.rto.tracker.domain.ZoneType;
import com.rto.tracker.dto.CreateZoneRequest;
import com.rto.tracker.dto.ErrorResponse;
import com.rto.tracker.dto.UpdateZoneRequest;
import com.rto.tracker.dto.ZoneResponse;
import com.rto.tracker.service.ZoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/zones")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Zones", description = "Zone management — locations tracked by Home Assistant")
public class ZoneController {

    private final ZoneService zoneService;

    @PostMapping
    @Operation(summary = "Create a zone", description = "Register a new zone for the authenticated user.")
    @ApiResponse(responseCode = "201", description = "Zone created")
    @ApiResponse(responseCode = "400", description = "Validation failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid API key")
    @ApiResponse(responseCode = "409", description = "Duplicate externalId",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ZoneResponse> createZone(@AuthenticationPrincipal User user,
                                                    @Valid @RequestBody CreateZoneRequest request) {
        log.info("Creating zone: userId={}, name={}, type={}, externalId={}", user.getId(), request.getName(), request.getType(), request.getExternalId());
        Zone zone = zoneService.createZone(user.getId(), user, request);
        log.info("Zone created: zoneId={}, userId={}", zone.getId(), user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ZoneResponse.from(zone));
    }

    @GetMapping
    @Operation(summary = "List zones", description = "List all zones for the authenticated user, optionally filtered by type and active status.")
    @ApiResponse(responseCode = "200", description = "Zones retrieved")
    @ApiResponse(responseCode = "401", description = "Missing or invalid API key")
    public ResponseEntity<List<ZoneResponse>> listZones(@AuthenticationPrincipal User user,
                                                         @RequestParam(required = false) ZoneType type,
                                                         @RequestParam(defaultValue = "true") boolean active) {
        log.info("Listing zones: userId={}, type={}, active={}", user.getId(), type, active);
        List<Zone> zones = zoneService.listZones(user.getId(), type, active);
        log.info("Listed {} zones for userId={}", zones.size(), user.getId());
        List<ZoneResponse> response = zones.stream().map(ZoneResponse::from).toList();
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{zoneId}")
    @Operation(summary = "Update a zone", description = "Update an existing zone. Only provided fields are changed.")
    @ApiResponse(responseCode = "200", description = "Zone updated")
    @ApiResponse(responseCode = "400", description = "Validation failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid API key")
    @ApiResponse(responseCode = "404", description = "Zone not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ZoneResponse> updateZone(@AuthenticationPrincipal User user,
                                                    @PathVariable UUID zoneId,
                                                    @Valid @RequestBody UpdateZoneRequest request) {
        log.info("Updating zone: userId={}, zoneId={}", user.getId(), zoneId);
        Zone zone = zoneService.updateZone(user.getId(), zoneId, request);
        log.info("Zone updated: zoneId={}, userId={}", zoneId, user.getId());
        return ResponseEntity.ok(ZoneResponse.from(zone));
    }

    @DeleteMapping("/{zoneId}")
    @Operation(summary = "Delete a zone", description = "Soft-delete a zone (sets active=false).")
    @ApiResponse(responseCode = "204", description = "Zone deleted")
    @ApiResponse(responseCode = "401", description = "Missing or invalid API key")
    @ApiResponse(responseCode = "404", description = "Zone not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Void> deleteZone(@AuthenticationPrincipal User user,
                                           @PathVariable UUID zoneId) {
        log.info("Deleting zone: userId={}, zoneId={}", user.getId(), zoneId);
        zoneService.deleteZone(user.getId(), zoneId);
        log.info("Zone deleted: zoneId={}, userId={}", zoneId, user.getId());
        return ResponseEntity.noContent().build();
    }
}
