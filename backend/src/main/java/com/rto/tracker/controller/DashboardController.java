package com.rto.tracker.controller;

import com.rto.tracker.domain.User;
import com.rto.tracker.dto.DashboardSummaryResponse;
import com.rto.tracker.dto.ErrorResponse;
import com.rto.tracker.dto.QuarterReportResponse;
import com.rto.tracker.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Dashboard", description = "RTO compliance dashboard and quarterly reports")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/dashboard/summary")
    @Operation(summary = "Get dashboard summary",
            description = "Returns RTO compliance stats for all periods (week, month, quarter, year) " +
                    "and recent commute information.")
    @ApiResponse(responseCode = "200", description = "Dashboard summary retrieved")
    @ApiResponse(responseCode = "401", description = "Missing or invalid API key")
    public ResponseEntity<DashboardSummaryResponse> getDashboardSummary(
            @AuthenticationPrincipal User user) {
        log.info("Dashboard summary requested: userId={}", user.getId());
        DashboardSummaryResponse summary = dashboardService.getSummary(user, LocalDate.now());
        log.info("Dashboard summary returned: userId={}, asOf={}", user.getId(), summary.getAsOf());
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/reports/available-periods")
    @Operation(summary = "Get available report periods",
            description = "Returns all year/quarter combinations that have event data.")
    @ApiResponse(responseCode = "200", description = "Available periods retrieved")
    @ApiResponse(responseCode = "401", description = "Missing or invalid API key")
    public ResponseEntity<List<Map<String, Integer>>> getAvailablePeriods(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(dashboardService.getAvailablePeriods(user));
    }

    @GetMapping("/reports/quarter/{year}/{quarter}")
    @Operation(summary = "Get quarter report",
            description = "Detailed report for a specific quarter including monthly breakdown.")
    @ApiResponse(responseCode = "200", description = "Quarter report retrieved")
    @ApiResponse(responseCode = "400", description = "Invalid quarter value",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid API key")
    public ResponseEntity<QuarterReportResponse> getQuarterReport(
            @AuthenticationPrincipal User user,
            @PathVariable int year,
            @PathVariable String quarter) {
        log.info("Quarter report requested: userId={}, year={}, quarter={}", user.getId(), year, quarter);
        int q = parseQuarter(quarter);
        QuarterReportResponse report = dashboardService.getQuarterReport(user, year, q);
        log.info("Quarter report returned: userId={}, period={}", user.getId(), report.getPeriod());
        return ResponseEntity.ok(report);
    }

    @GetMapping("/reports/quarter/current")
    @Operation(summary = "Get current quarter report",
            description = "Shortcut for the current quarter's report.")
    @ApiResponse(responseCode = "200", description = "Current quarter report retrieved")
    @ApiResponse(responseCode = "401", description = "Missing or invalid API key")
    public ResponseEntity<QuarterReportResponse> getCurrentQuarterReport(
            @AuthenticationPrincipal User user) {
        log.info("Current quarter report requested: userId={}", user.getId());
        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int quarter = (today.getMonthValue() - 1) / 3 + 1;
        QuarterReportResponse report = dashboardService.getQuarterReport(user, year, quarter);
        log.info("Current quarter report returned: userId={}, period={}", user.getId(), report.getPeriod());
        return ResponseEntity.ok(report);
    }

    private int parseQuarter(String quarter) {
        String q = quarter.toUpperCase().replace("Q", "");
        int val = Integer.parseInt(q);
        if (val < 1 || val > 4) {
            throw new IllegalArgumentException("Quarter must be between 1 and 4 (or Q1-Q4)");
        }
        return val;
    }
}
