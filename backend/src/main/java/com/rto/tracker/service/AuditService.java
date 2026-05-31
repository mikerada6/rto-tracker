package com.rto.tracker.service;

import com.rto.tracker.domain.OfficeDayRecord;
import com.rto.tracker.domain.User;
import com.rto.tracker.domain.Zone;
import com.rto.tracker.dto.AuditResponse;
import com.rto.tracker.repository.OfficeDayRecordRepository;
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

    private final OfficeDayRecordRepository recordRepository;
    private final OfficeDayCalculationService calculationService;

    @Transactional
    public AuditResponse getOfficeDayAudit(User user, LocalDate startDate, LocalDate endDate) {
        log.info("Audit requested: userId={}, startDate={}, endDate={}", user.getId(), startDate, endDate);

        LocalDate effectiveEnd = endDate.isAfter(LocalDate.now()) ? LocalDate.now() : endDate;

        ensureRecordsComputed(user, startDate, effectiveEnd);

        List<OfficeDayRecord> records = recordRepository.findByUserIdAndDateRange(
                user.getId(), startDate, effectiveEnd);

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

    private void ensureRecordsComputed(User user, LocalDate start, LocalDate end) {
        LocalDate date = start;
        while (!date.isAfter(end)) {
            calculationService.getOrCompute(user.getId(), user, date);
            date = date.plusDays(1);
        }
    }
}
