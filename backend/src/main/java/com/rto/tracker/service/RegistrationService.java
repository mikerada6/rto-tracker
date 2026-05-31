package com.rto.tracker.service;

import com.rto.tracker.domain.InviteCode;
import com.rto.tracker.domain.User;
import com.rto.tracker.dto.RegisterRequest;
import com.rto.tracker.dto.RegisterResponse;
import com.rto.tracker.exception.DuplicateResourceException;
import com.rto.tracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {

    private final UserRepository userRepository;
    private final InviteCodeService inviteCodeService;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // Validate invite code
        InviteCode inviteCode = inviteCodeService.validateAndGet(request.getInviteCode());

        // Check email uniqueness
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new DuplicateResourceException("A user with this email already exists");
        }

        // Generate API key
        String plaintextKey = UserService.generateApiKey();
        String keyHash = UserService.hashApiKey(plaintextKey);

        // Create user
        User user = User.builder()
                .email(request.getEmail())
                .displayName(request.getDisplayName())
                .apiKeyHash(keyHash)
                .requiredDaysPerWeek(request.getRequiredDaysPerWeek())
                .build();
        User saved = userRepository.save(user);

        // Mark invite code as used
        inviteCodeService.markUsed(inviteCode, saved);

        log.info("New user registered: id={}, email={}", saved.getId(), saved.getEmail());
        return RegisterResponse.from(saved, plaintextKey);
    }
}
