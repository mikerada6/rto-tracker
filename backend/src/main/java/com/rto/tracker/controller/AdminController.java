package com.rto.tracker.controller;

import com.rto.tracker.domain.InviteCode;
import com.rto.tracker.domain.User;
import com.rto.tracker.dto.*;
import com.rto.tracker.service.InviteCodeService;
import com.rto.tracker.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin", description = "Admin-only endpoints for user and invite code management")
public class AdminController {

    private final InviteCodeService inviteCodeService;
    private final UserService userService;

    @PostMapping("/invite-codes")
    @Operation(summary = "Generate a new invite code",
            description = "Creates a single-use invite code with the specified expiry. Requires ADMIN role.")
    @ApiResponse(responseCode = "201", description = "Invite code created")
    @ApiResponse(responseCode = "403", description = "Not an admin")
    public ResponseEntity<InviteCodeResponse> createInviteCode(@AuthenticationPrincipal User admin,
                                                                @Valid @RequestBody CreateInviteCodeRequest request) {
        log.info("Admin creating invite code: adminId={}, expiresInDays={}", admin.getId(), request.getExpiresInDays());
        InviteCode code = inviteCodeService.createInviteCode(admin, request.getExpiresInDays());
        log.info("Invite code created: adminId={}, codeId={}", admin.getId(), code.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(InviteCodeResponse.from(code));
    }

    @GetMapping("/invite-codes")
    @Operation(summary = "List all invite codes",
            description = "Returns all invite codes with their status (available, used, expired). Requires ADMIN role.")
    @ApiResponse(responseCode = "200", description = "Invite codes retrieved")
    @ApiResponse(responseCode = "403", description = "Not an admin")
    public ResponseEntity<List<InviteCodeResponse>> listInviteCodes() {
        log.info("Admin listing invite codes");
        List<InviteCodeResponse> codes = inviteCodeService.listAll().stream()
                .map(InviteCodeResponse::from)
                .toList();
        return ResponseEntity.ok(codes);
    }

    @GetMapping("/users")
    @Operation(summary = "List all users",
            description = "Returns all registered users with their status and role. Requires ADMIN role.")
    @ApiResponse(responseCode = "200", description = "Users retrieved")
    @ApiResponse(responseCode = "403", description = "Not an admin")
    public ResponseEntity<List<AdminUserResponse>> listUsers() {
        log.info("Admin listing users");
        List<AdminUserResponse> users = userService.listAllUsers().stream()
                .map(AdminUserResponse::from)
                .toList();
        return ResponseEntity.ok(users);
    }

    @PostMapping("/users/{userId}/deactivate")
    @Operation(summary = "Deactivate a user",
            description = "Sets the user's active flag to false. Their API key stops working immediately. Requires ADMIN role.")
    @ApiResponse(responseCode = "200", description = "User deactivated")
    @ApiResponse(responseCode = "404", description = "User not found")
    @ApiResponse(responseCode = "403", description = "Not an admin")
    public ResponseEntity<AdminUserResponse> deactivateUser(@PathVariable UUID userId) {
        log.info("Admin deactivating user: userId={}", userId);
        User user = userService.deactivateUser(userId);
        return ResponseEntity.ok(AdminUserResponse.from(user));
    }

    @PostMapping("/users/{userId}/activate")
    @Operation(summary = "Activate a user",
            description = "Re-activates a previously deactivated user. Requires ADMIN role.")
    @ApiResponse(responseCode = "200", description = "User activated")
    @ApiResponse(responseCode = "404", description = "User not found")
    @ApiResponse(responseCode = "403", description = "Not an admin")
    public ResponseEntity<AdminUserResponse> activateUser(@PathVariable UUID userId) {
        log.info("Admin activating user: userId={}", userId);
        User user = userService.activateUser(userId);
        return ResponseEntity.ok(AdminUserResponse.from(user));
    }

    @PatchMapping("/users/{userId}")
    @Operation(summary = "Update a user's profile",
            description = "Admin can update any user's display name or required days per week. Requires ADMIN role.")
    @ApiResponse(responseCode = "200", description = "User updated")
    @ApiResponse(responseCode = "404", description = "User not found")
    @ApiResponse(responseCode = "403", description = "Not an admin")
    public ResponseEntity<AdminUserResponse> updateUser(@PathVariable UUID userId,
                                                         @Valid @RequestBody AdminUpdateUserRequest request) {
        log.info("Admin updating user: userId={}", userId);
        User user = userService.adminUpdateUser(userId, request);
        return ResponseEntity.ok(AdminUserResponse.from(user));
    }
}
