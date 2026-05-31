package com.rto.tracker.dto;

import com.rto.tracker.domain.InviteCode;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InviteCodeResponse {

    private UUID id;
    private String code;
    private String status;
    private UUID createdBy;
    private UUID usedBy;
    private Instant usedAt;
    private Instant expiresAt;
    private Instant createdAt;

    public static InviteCodeResponse from(InviteCode inviteCode) {
        return InviteCodeResponse.builder()
                .id(inviteCode.getId())
                .code(inviteCode.getCode())
                .status(inviteCode.getStatus())
                .createdBy(inviteCode.getCreatedBy().getId())
                .usedBy(inviteCode.getUsedBy() != null ? inviteCode.getUsedBy().getId() : null)
                .usedAt(inviteCode.getUsedAt())
                .expiresAt(inviteCode.getExpiresAt())
                .createdAt(inviteCode.getCreatedAt())
                .build();
    }
}
