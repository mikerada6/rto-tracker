package com.rto.tracker.service;

import com.rto.tracker.domain.InviteCode;
import com.rto.tracker.domain.User;
import com.rto.tracker.repository.InviteCodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InviteCodeServiceTest {

    @Mock
    private InviteCodeRepository inviteCodeRepository;

    @InjectMocks
    private InviteCodeService inviteCodeService;

    @Test
    void createInviteCode_generatesCodeWithPrefix() {
        User admin = User.builder().id(UUID.randomUUID()).email("admin@test.com")
                .displayName("Admin").apiKeyHash("hash").build();

        when(inviteCodeRepository.save(any(InviteCode.class)))
                .thenAnswer(invocation -> {
                    InviteCode ic = invocation.getArgument(0);
                    ic.setId(UUID.randomUUID());
                    return ic;
                });

        InviteCode result = inviteCodeService.createInviteCode(admin, 7);

        assertThat(result.getCode()).startsWith("rto-");
        assertThat(result.getCode()).hasSize(20); // "rto-" + 16 random chars
        assertThat(result.getExpiresAt()).isAfter(Instant.now());
        assertThat(result.getCreatedBy()).isEqualTo(admin);
    }

    @Test
    void validateAndGet_validCode_returnsCode() {
        InviteCode code = InviteCode.builder()
                .id(UUID.randomUUID())
                .code("rto-validcode1234567")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .createdBy(User.builder().id(UUID.randomUUID()).build())
                .build();

        when(inviteCodeRepository.findByCode("rto-validcode1234567")).thenReturn(Optional.of(code));

        InviteCode result = inviteCodeService.validateAndGet("rto-validcode1234567");
        assertThat(result).isEqualTo(code);
    }

    @Test
    void validateAndGet_expiredCode_throwsException() {
        InviteCode code = InviteCode.builder()
                .id(UUID.randomUUID())
                .code("rto-expired12345678")
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .createdBy(User.builder().id(UUID.randomUUID()).build())
                .build();

        when(inviteCodeRepository.findByCode("rto-expired12345678")).thenReturn(Optional.of(code));

        assertThatThrownBy(() -> inviteCodeService.validateAndGet("rto-expired12345678"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void validateAndGet_usedCode_throwsException() {
        User usedByUser = User.builder().id(UUID.randomUUID()).build();
        InviteCode code = InviteCode.builder()
                .id(UUID.randomUUID())
                .code("rto-usedcode12345678")
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .createdBy(User.builder().id(UUID.randomUUID()).build())
                .usedBy(usedByUser)
                .usedAt(Instant.now())
                .build();

        when(inviteCodeRepository.findByCode("rto-usedcode12345678")).thenReturn(Optional.of(code));

        assertThatThrownBy(() -> inviteCodeService.validateAndGet("rto-usedcode12345678"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    void validateAndGet_nonexistentCode_throwsException() {
        when(inviteCodeRepository.findByCode("rto-doesnotexist1234")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inviteCodeService.validateAndGet("rto-doesnotexist1234"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid invite code");
    }
}
