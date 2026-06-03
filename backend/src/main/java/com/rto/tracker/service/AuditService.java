package com.rto.tracker.service;

import com.rto.tracker.domain.OfficeDayRecord;
import com.rto.tracker.domain.User;
import com.rto.tracker.domain.Zone;
import com.rto.tracker.dto.AuditResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditService {

    private final OfficeDayCalculationService calculationService;

    @Transactional
    public AuditResponse getOfficeDayAudit(User user, LocalDate startDate, LocalDate endDate) {
        log.info("Audit requested: userId={}, startDate={}, endDate={}", user.getId(), startDate, endDate);

        LocalDate effectiveEnd = endDate.isAfter(LocalDate.now()) ? LocalDate.now() : endDate;

        // Bulk ensure + fetch in one shot
        List<OfficeDayRecord> records = calculationService.ensureRangeComputed(user, startDate, effectiveEnd);

        List<AuditResponse.AuditDayEntry> days = records.stream()
                .filter(r -> !r.getOfficesVisited().isEmpty())
                .map(this::toAuditDayEntry)
                .toList();

        log.info("Audit returned: userId={}, range={} to {}, officeDays={}",
                user.getId(), startDate, effectiveEnd, days.size());

        return AuditResponse.builder()
                .startDate(startDate)
                .endDate(effectiveEnd)
                .totalOfficeDays(days.size())
                .days(days)
                .build();
    }

    private AuditResponse.AuditDayEntry toAuditDayEntry(OfficeDayRecord record) {
        long officeSeconds = record.getTotalOfficeTime() != null ? record.getTotalOfficeTime() : 0;
        return AuditResponse.AuditDayEntry.builder()
                .date(record.getDate())
                .officesVisited(record.getOfficesVisited().stream()
                        .map(Zone::getName)
                        .sorted()
                        .toList())
                .totalOfficeTime(DurationFormatter.format(officeSeconds))
                .totalOfficeTimeSeconds(officeSeconds)
                .firstOfficeEntry(record.getFirstOfficeEntry())
                .lastOfficeExit(record.getLastOfficeExit())
                .build();
    }
}



