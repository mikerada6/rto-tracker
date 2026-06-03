package com.rto.tracker.controller;

import com.rto.tracker.domain.CommuteAnnotation;
import com.rto.tracker.domain.User;
import com.rto.tracker.dto.CommuteAnnotationDto;
import com.rto.tracker.dto.ErrorResponse;
import com.rto.tracker.service.CommuteAnnotationService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/days/{date}/commute-annotations")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Commute Annotations", description = "Label non-commute time within a commute window (e.g. happy hour)")
public class CommuteAnnotationController {

    private final CommuteAnnotationService service;

    @PostMapping
    @Operation(summary = "Create a commute annotation",
            description = "Marks a time window inside the day's commute as non-commute time of the given category.")
    @ApiResponse(responseCode = "201", description = "Annotation created")
    @ApiResponse(responseCode = "400", description = "Invalid window or validation failure",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid API key")
    public ResponseEntity<CommuteAnnotationDto.Response> create(
            @AuthenticationPrincipal User user,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody CommuteAnnotationDto.CreateRequest req) {
        log.info("Creating commute annotation: userId={}, date={}, category={}", user.getId(), date, req.getCategory());
        CommuteAnnotation ann = service.create(user, date, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(CommuteAnnotationDto.Response.from(ann));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a commute annotation", description = "Update category or note. Time window is immutable.")
    @ApiResponse(responseCode = "200", description = "Annotation updated")
    @ApiResponse(responseCode = "404", description = "Annotation not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<CommuteAnnotationDto.Response> update(
            @AuthenticationPrincipal User user,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable UUID id,
            @Valid @RequestBody CommuteAnnotationDto.UpdateRequest req) {
        log.info("Updating commute annotation: userId={}, id={}", user.getId(), id);
        CommuteAnnotation ann = service.update(user, id, req);
        return ResponseEntity.ok(CommuteAnnotationDto.Response.from(ann));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a commute annotation")
    @ApiResponse(responseCode = "204", description = "Annotation deleted")
    @ApiResponse(responseCode = "404", description = "Annotation not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal User user,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable UUID id) {
        log.info("Deleting commute annotation: userId={}, id={}", user.getId(), id);
        service.delete(user, id);
        return ResponseEntity.noContent().build();
    }
}
