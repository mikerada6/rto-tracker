package com.rto.tracker.service;

import com.rto.tracker.domain.User;
import com.rto.tracker.dto.AdminUpdateUserRequest;
import com.rto.tracker.dto.UpdateUserRequest;
import com.rto.tracker.exception.BusinessRuleException;
import com.rto.tracker.exception.EntityNotFoundException;
import com.rto.tracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Transactional
    public User updateUser(User user, UpdateUserRequest request) {
        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName());
        }
        if (request.getRequiredDaysPerWeek() != null) {
            user.setRequiredDaysPerWeek(request.getRequiredDaysPerWeek());
        }
        if (request.getTimezone() != null) {
            try {
                ZoneId.of(request.getTimezone()); // validate
            } catch (Exception e) {
                throw new BusinessRuleException("Invalid timezone: " + request.getTimezone());
            }
            user.setTimezone(request.getTimezone());
        }
        if (request.getCommuteAnomalyThresholdMinutes() != null) {
            user.setCommuteAnomalyThresholdMinutes(request.getCommuteAnomalyThresholdMinutes());
        }
        User saved = userRepository.save(user);
        log.info("User profile updated: id={}", user.getId());
        return saved;
    }

    @Transactional
    public String regenerateApiKey(User user) {
        String newKey = generateApiKey();
        String hash = hashApiKey(newKey);
        user.setApiKeyHash(hash);
        userRepository.save(user);
        log.info("API key regenerated for user: id={}", user.getId());
        return newKey;
    }

    @Transactional(readOnly = true)
    public List<User> listAllUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public User deactivateUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        user.setActive(false);
        User saved = userRepository.save(user);
        log.info("User deactivated: id={}", userId);
        return saved;
    }

    @Transactional
    public User activateUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        user.setActive(true);
        User saved = userRepository.save(user);
        log.info("User activated: id={}", userId);
        return saved;
    }

    @Transactional
    public User adminUpdateUser(UUID userId, AdminUpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName());
        }
        if (request.getRequiredDaysPerWeek() != null) {
            user.setRequiredDaysPerWeek(request.getRequiredDaysPerWeek());
        }
        User saved = userRepository.save(user);
        log.info("User updated by admin: id={}", userId);
        return saved;
    }

    public static String generateApiKey() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hashApiKey(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
