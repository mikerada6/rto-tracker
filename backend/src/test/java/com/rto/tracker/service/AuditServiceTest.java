package com.rto.tracker.service;

import com.rto.tracker.domain.*;
import com.rto.tracker.dto.AuditResponse;
import com.rto.tracker.repository.OfficeDayRecordRepository;
import com.rto.tracker.repository.ZoneEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private OfficeDayRecordRepository recordRepository;

    @Mock
    private ZoneEventRepository eventRepository;

    private OfficeDayCalculationService calculationService;

    private AuditService auditService;
    private User testUser;
    private Zone officeZone;
    private Zone secondOffice;

    @BeforeEach
    void setUp() {
        calculationService = new OfficeDayCalculationService(eventRepository, recordRepository, new SimpleMeterRegistry());
        // AuditService now only needs OfficeDayCalculationService
        auditService = new AuditService(calculationService);

        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .displayName("Test User")
                .timezone("UTC")
                .build();

        officeZone = Zone.builder()
                .id(UUID.randomUUID())
                .name("Downtown Office")
                .type(ZoneType.OFFICE)
                .build();

        secondOffice = Zone.builder()
                .id(UUID.randomUUID())
                .name("Midtown Office")
                .type(ZoneType.OFFICE)
                .build();
    }

    @Test
    void getOfficeDayAudit_returnsOnlyOfficeDays() {
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 5, 3);

        OfficeDayRecord officeDay = buildRecord(LocalDate.of(2026, 5, 1), Set.of(officeZone), 28800L);
        OfficeDayRecord nonOfficeDay = buildRecord(LocalDate.of(2026, 5, 2), Set.of(), 0L);
        OfficeDayRecord officeDay2 = buildRecord(LocalDate.of(2026, 5, 3), Set.of(officeZone), 14400L);

        // ensureRangeComputed bulk-fetches existing records in one call
        when(recordRepository.findByUserIdAndDateRange(eq(testUser.getId()), eq(start), eq(end)))
                .thenReturn(List.of(officeDay, nonOfficeDay, officeDay2));

        AuditResponse response = auditService.getOfficeDayAudit(testUser, start, end);

        assertThat(response.getTotalOfficeDays()).isEqualTo(2);
        assertThat(response.getDays()).hasSize(2);
        assertThat(response.getDays().get(0).getDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(response.getDays().get(1).getDate()).isEqualTo(LocalDate.of(2026, 5, 3));
    }

    @Test
    void getOfficeDayAudit_formatsOfficeTime() {
        LocalDate date = LocalDate.of(2026, 5, 1);

        OfficeDayRecord record = buildRecord(date, Set.of(officeZone), 27900L); // 7h 45m

        when(recordRepository.findByUserIdAndDateRange(any(), any(), any()))
                .thenReturn(List.of(record));

        AuditResponse response = auditService.getOfficeDayAudit(testUser, date, date);

        AuditResponse.AuditDayEntry entry = response.getDays().get(0);
        assertThat(entry.getTotalOfficeTime()).isEqualTo("7h 45m");
        assertThat(entry.getTotalOfficeTimeSeconds()).isEqualTo(27900L);
    }

    @Test
    void getOfficeDayAudit_multipleOfficesSorted() {
        LocalDate date = LocalDate.of(2026, 5, 1);

        OfficeDayRecord record = buildRecord(date, Set.of(secondOffice, officeZone), 28800L);

        when(recordRepository.findByUserIdAndDateRange(any(), any(), any()))
                .thenReturn(List.of(record));

        AuditResponse response = auditService.getOfficeDayAudit(testUser, date, date);

        assertThat(response.getDays().get(0).getOfficesVisited())
                .containsExactly("Downtown Office", "Midtown Office");
    }

    @Test
    void getOfficeDayAudit_capsEndDateAtToday() {
        LocalDate start = LocalDate.now().minusDays(5);
        LocalDate futureEnd = LocalDate.now().plusDays(10);

        // No existing records in range; compute will be called for each day
        when(recordRepository.findByUserIdAndDateRange(eq(testUser.getId()), eq(start), eq(LocalDate.now())))
                .thenReturn(List.of());
        when(eventRepository.findByUserIdAndTimestampRange(any(), any(), any())).thenReturn(List.of());
        when(recordRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        AuditResponse response = auditService.getOfficeDayAudit(testUser, start, futureEnd);

        assertThat(response.getEndDate()).isEqualTo(LocalDate.now());
        verify(recordRepository).findByUserIdAndDateRange(testUser.getId(), start, LocalDate.now());
    }

    @Test
    void getOfficeDayAudit_emptyRange() {
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 5, 7);

        // No existing records → compute all, but events are empty so no office visits
        when(recordRepository.findByUserIdAndDateRange(any(), any(), any())).thenReturn(List.of());
        when(eventRepository.findByUserIdAndTimestampRange(any(), any(), any())).thenReturn(List.of());
        when(recordRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        AuditResponse response = auditService.getOfficeDayAudit(testUser, start, end);

        assertThat(response.getTotalOfficeDays()).isZero();
        assertThat(response.getDays()).isEmpty();
    }

    @Test
    void getOfficeDayAudit_ensuresRecordsComputed() {
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 5, 3);

        // No existing records — ensureRangeComputed will compute missing dates
        when(recordRepository.findByUserIdAndDateRange(any(), any(), any())).thenReturn(List.of());
        when(eventRepository.findByUserIdAndTimestampRange(any(), any(), any())).thenReturn(List.of());
        when(recordRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        auditService.getOfficeDayAudit(testUser, start, end);

        // ensureRangeComputed fetches all existing in one bulk query
        verify(recordRepository).findByUserIdAndDateRange(testUser.getId(), start, end);
        // Missing dates are batch-saved
        verify(recordRepository).saveAll(anyList());
    }

    private OfficeDayRecord buildRecord(LocalDate date, Set<Zone> offices, long officeTimeSeconds) {
        Instant entry = date.atTime(9, 0).toInstant(java.time.ZoneOffset.UTC);
        Instant exit = date.atTime(17, 0).toInstant(java.time.ZoneOffset.UTC);
        return OfficeDayRecord.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .date(date)
                .totalOfficeTime(officeTimeSeconds)
                .firstOfficeEntry(offices.isEmpty() ? null : entry)
                .lastOfficeExit(offices.isEmpty() ? null : exit)
                .officesVisited(offices)
                .build();
    }
}
