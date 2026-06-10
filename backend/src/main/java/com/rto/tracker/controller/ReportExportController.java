package com.rto.tracker.controller;

import com.rto.tracker.domain.User;
import com.rto.tracker.dto.ErrorResponse;
import com.rto.tracker.dto.ReportPeriod;
import com.rto.tracker.service.ReportExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports/export")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Report Export", description = "Export RTO compliance reports as PDF")
public class ReportExportController {

    private final ReportExportService reportExportService;

    @GetMapping("/pdf")
    @Operation(summary = "Export a compliance report as PDF",
            description = "Generates a printable PDF report for the requested period. " +
                    "For 'custom', both 'from' and 'to' must be supplied (max 365 days).")
    @ApiResponse(responseCode = "200", description = "PDF generated",
            content = @Content(mediaType = "application/pdf"))
    @ApiResponse(responseCode = "400", description = "Invalid period or date range",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid API key")
    public ResponseEntity<byte[]> exportPdf(
            @AuthenticationPrincipal User user,
            @RequestParam(name = "period", defaultValue = "MONTH") ReportPeriod period,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        log.info("PDF report export requested: userId={}, period={}, from={}, to={}",
                user.getId(), period, from, to);

        byte[] pdf = reportExportService.generatePdf(user, period, from, to);

        String filename = buildFilename(user, period, from, to);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename)
                .build());
        headers.setContentLength(pdf.length);

        log.info("PDF report export delivered: userId={}, bytes={}, filename={}",
                user.getId(), pdf.length, filename);

        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    private String buildFilename(User user, ReportPeriod period, LocalDate from, LocalDate to) {
        String slug = user.getDisplayName().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.isEmpty()) slug = "user";
        String suffix = switch (period) {
            case CUSTOM -> (from != null ? from : LocalDate.now()) + "_" + (to != null ? to : LocalDate.now());
            default -> period.name().toLowerCase();
        };
        return "rto-report-" + slug + "-" + suffix + ".pdf";
    }
}
