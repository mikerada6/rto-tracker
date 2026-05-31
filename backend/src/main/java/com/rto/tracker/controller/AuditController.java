package com.rto.tracker.controller;

import com.rto.tracker.domain.User;
import com.rto.tracker.dto.AuditResponse;
import com.rto.tracker.dto.ErrorResponse;
import com.rto.tracker.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Audit", description = "Office attendance audit trail")
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/office-days")
    @Operation(summary = "Get office day audit log",
            description = "Returns a list of days the user went to the office within the specified " +
                    "date range, including offices visited and time spent. Defaults to current quarter.")
    @ApiResponse(responseCode = "200", description = "Audit log retrieved")
    @ApiResponse(responseCode = "400", description = "Invalid date range",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid API key")
    public ResponseEntity<AuditResponse> getOfficeDayAudit(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @Parameter(description = "Start date (inclusive). Defaults to start of current quarter.")
            LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @Parameter(description = "End date (inclusive). Defaults to end of current quarter.")
            LocalDate endDate) {

        LocalDate today = LocalDate.now();

        if (startDate == null) {
            int quarterMonth = ((today.getMonthValue() - 1) / 3) * 3 + 1;
            startDate = LocalDate.of(today.getYear(), quarterMonth, 1);
        }
        if (endDate == null) {
            int quarterEndMonth = ((today.getMonthValue() - 1) / 3) * 3 + 3;
            endDate = LocalDate.of(today.getYear(), quarterEndMonth, 1)
                    .withDayOfMonth(LocalDate.of(today.getYear(), quarterEndMonth, 1).lengthOfMonth());
        }

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate must not be after endDate");
        }

        log.info("Office day audit requested: userId={}, startDate={}, endDate={}",
                user.getId(), startDate, endDate);

        AuditResponse response = auditService.getOfficeDayAudit(user, startDate, endDate);
        return ResponseEntity.ok(response);
    }
}
