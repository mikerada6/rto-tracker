package com.rto.tracker.repository;

import com.rto.tracker.domain.EventType;
import com.rto.tracker.domain.ZoneEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ZoneEventRepository extends JpaRepository<ZoneEvent, UUID> {

    @Query(value = "SELECT EXTRACT(YEAR FROM timestamp)::int AS yr, " +
                   "EXTRACT(QUARTER FROM timestamp)::int AS q " +
                   "FROM zone_events WHERE user_id = :userId " +
                   "GROUP BY yr, q ORDER BY yr, q",
           nativeQuery = true)
    List<Object[]> findDistinctYearQuartersByUserId(@Param("userId") UUID userId);

    @Query("SELECT e FROM ZoneEvent e JOIN FETCH e.zone WHERE e.user.id = :userId " +
           "AND e.timestamp >= :start AND e.timestamp < :end ORDER BY e.timestamp ASC")
    List<ZoneEvent> findByUserIdAndTimestampRange(
            @Param("userId") UUID userId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("SELECT e FROM ZoneEvent e WHERE e.user.id = :userId AND e.zone.id = :zoneId " +
           "AND e.eventType = :eventType AND e.timestamp >= :windowStart AND e.timestamp <= :windowEnd")
    List<ZoneEvent> findDuplicates(
            @Param("userId") UUID userId,
            @Param("zoneId") UUID zoneId,
            @Param("eventType") EventType eventType,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd);
}
