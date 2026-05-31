package com.rto.tracker.repository;

import com.rto.tracker.domain.OfficeDayRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfficeDayRecordRepository extends JpaRepository<OfficeDayRecord, UUID> {

    Optional<OfficeDayRecord> findByUserIdAndDate(UUID userId, LocalDate date);

    void deleteByUserIdAndDate(UUID userId, LocalDate date);

    @Query("SELECT r FROM OfficeDayRecord r LEFT JOIN FETCH r.officesVisited " +
           "WHERE r.user.id = :userId AND r.date >= :startDate AND r.date <= :endDate " +
           "ORDER BY r.date DESC")
    List<OfficeDayRecord> findByUserIdAndDateRange(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
