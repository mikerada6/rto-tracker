package com.rto.tracker.repository;

import com.rto.tracker.domain.InviteCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InviteCodeRepository extends JpaRepository<InviteCode, UUID> {

    Optional<InviteCode> findByCode(String code);

    List<InviteCode> findByCreatedById(UUID createdById);

    List<InviteCode> findAllByOrderByCreatedAtDesc();
}
