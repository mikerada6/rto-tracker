package com.rto.tracker.service;

import com.rto.tracker.domain.CommuteAnnotation;
import com.rto.tracker.domain.User;
import com.rto.tracker.dto.CommuteAnnotationDto;
import com.rto.tracker.exception.EntityNotFoundException;
import com.rto.tracker.repository.CommuteAnnotationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommuteAnnotationService {

    private final CommuteAnnotationRepository repository;
    private final OfficeDayCalculationService calculationService;

    @Transactional(readOnly = true)
    public List<CommuteAnnotation> listForDay(UUID userId, LocalDate date) {
        return repository.findByUserIdAndDateOrderByStartTimeAsc(userId, date);
    }

    @Transactional
    public CommuteAnnotation create(User user, LocalDate date, CommuteAnnotationDto.CreateRequest req) {
        if (!req.getEndTime().isAfter(req.getStartTime())) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
        ZoneId tz = ZoneId.of(user.getTimezone());
        Instant dayStart = date.atStartOfDay(tz).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(tz).toInstant();
        if (req.getStartTime().isBefore(dayStart) || req.getEndTime().isAfter(dayEnd)) {
            throw new IllegalArgumentException("annotation window must fall within the date " + date);
        }

        CommuteAnnotation ann = CommuteAnnotation.builder()
                .user(user)
                .date(date)
                .startTime(req.getStartTime())
                .endTime(req.getEndTime())
                .category(req.getCategory())
                .note(req.getNote())
                .build();

        CommuteAnnotation saved = repository.save(ann);
        calculationService.invalidate(user.getId(), date);
        log.info("CommuteAnnotation created: userId={}, date={}, id={}, category={}, durationSecs={}",
                user.getId(), date, saved.getId(), saved.getCategory(),
                saved.getEndTime().getEpochSecond() - saved.getStartTime().getEpochSecond());
        return saved;
    }

    @Transactional
    public CommuteAnnotation update(User user, UUID id, CommuteAnnotationDto.UpdateRequest req) {
        CommuteAnnotation ann = repository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new EntityNotFoundException("CommuteAnnotation not found: " + id));
        ann.setCategory(req.getCategory());
        ann.setNote(req.getNote());
        CommuteAnnotation saved = repository.save(ann);
        calculationService.invalidate(user.getId(), ann.getDate());
        log.info("CommuteAnnotation updated: userId={}, id={}, category={}", user.getId(), id, saved.getCategory());
        return saved;
    }

    @Transactional
    public void delete(User user, UUID id) {
        CommuteAnnotation ann = repository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new EntityNotFoundException("CommuteAnnotation not found: " + id));
        LocalDate date = ann.getDate();
        repository.delete(ann);
        calculationService.invalidate(user.getId(), date);
        log.info("CommuteAnnotation deleted: userId={}, id={}, date={}", user.getId(), id, date);
    }
}
