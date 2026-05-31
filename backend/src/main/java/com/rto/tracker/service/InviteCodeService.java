package com.rto.tracker.service;

import com.rto.tracker.domain.InviteCode;
import com.rto.tracker.domain.User;
import com.rto.tracker.repository.InviteCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InviteCodeService {

    private final InviteCodeRepository inviteCodeRepository;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String CODE_PREFIX = "rto-";
    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int CODE_RANDOM_LENGTH = 16;

    @Transactional
    public InviteCode createInviteCode(User admin, int expiresInDays) {
        String code = generateCode();
        InviteCode inviteCode = InviteCode.builder()
                .code(code)
                .createdBy(admin)
                .expiresAt(Instant.now().plus(expiresInDays, ChronoUnit.DAYS))
                .build();
        InviteCode saved = inviteCodeRepository.save(inviteCode);
        log.info("Invite code created by admin: userId={}, code={}, expiresInDays={}",
                admin.getId(), code, expiresInDays);
        return saved;
    }

    @Transactional(readOnly = true)
    public InviteCode validateAndGet(String code) {
        InviteCode inviteCode = inviteCodeRepository.findByCode(code)
                .orElseThrow(() -> {
                    log.warn("Invalid invite code attempted: code={}", code);
                    return new IllegalArgumentException("Invalid invite code");
                });

        if (inviteCode.isUsed()) {
            log.warn("Already-used invite code attempted: codeId={}", inviteCode.getId());
            throw new IllegalArgumentException("Invite code has already been used");
        }
        if (inviteCode.isExpired()) {
            log.warn("Expired invite code attempted: codeId={}, expiresAt={}", inviteCode.getId(), inviteCode.getExpiresAt());
            throw new IllegalArgumentException("Invite code has expired");
        }
        log.info("Invite code validated: codeId={}", inviteCode.getId());
        return inviteCode;
    }

    @Transactional
    public void markUsed(InviteCode inviteCode, User user) {
        inviteCode.setUsedBy(user);
        inviteCode.setUsedAt(Instant.now());
        inviteCodeRepository.save(inviteCode);
        log.info("Invite code used: codeId={}, userId={}", inviteCode.getId(), user.getId());
    }

    @Transactional(readOnly = true)
    public List<InviteCode> listAll() {
        return inviteCodeRepository.findAllByOrderByCreatedAtDesc();
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_PREFIX);
        for (int i = 0; i < CODE_RANDOM_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(SECURE_RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
