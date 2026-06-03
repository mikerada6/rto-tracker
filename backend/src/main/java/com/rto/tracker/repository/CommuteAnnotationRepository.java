package com.rto.tracker.repository;

import com.rto.tracker.domain.CommuteAnnotation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommuteAnnotationRepository extends JpaRepository<CommuteAnnotation, UUID> {

    List<CommuteAnnotation> findByUserIdAndDateOrderByStartTimeAsc(UUID userId, LocalDate date);

    Optional<CommuteAnnotation> findByIdAndUserId(UUID id, UUID userId);

    void deleteByUserIdAndDate(UUID userId, LocalDate date);
}
