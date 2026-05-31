package com.rto.tracker.controller;

import com.rto.tracker.domain.User;
import com.rto.tracker.dto.*;
import com.rto.tracker.exception.BusinessRuleException;
import com.rto.tracker.service.RegistrationService;
import com.rto.tracker.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Users", description = "User profile, registration, and API key management")
public class UserController {

    private final UserService userService;
    private final RegistrationService registrationService;

    @Value("${app.registration-enabled:false}")
    private boolean registrationEnabled;

    @PostMapping("/register")
    @Operation(summary = "Register a new user",
            description = "Register a new user with a valid invite code. " +
                    "This endpoint is disabled by default — enable via APP_REGISTRATION_ENABLED=true.")
    @ApiResponse(responseCode = "201", description = "User registered successfully")
    @ApiResponse(responseCode = "400", description = "Invalid or expired invite code, or validation failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Registration is disabled")
    @ApiResponse(responseCode = "409", description = "Email already in use",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (!registrationEnabled) {
            log.warn("Registration attempt rejected: registration is disabled, email={}", request.getEmail());
            throw new BusinessRuleException("Registration is currently disabled");
        }
        log.info("Registration attempt: email={}", request.getEmail());
        RegisterResponse response = registrationService.register(request);
        log.info("Registration successful: userId={}, email={}", response.getId(), request.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user profile",
            description = "Returns the profile of the authenticated user.")
    @ApiResponse(responseCode = "200", description = "User profile retrieved")
    @ApiResponse(responseCode = "401", description = "Missing or invalid API key")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PatchMapping("/me")
    @Operation(summary = "Update current user profile",
            description = "Update display name and/or required days per week for the authenticated user.")
    @ApiResponse(responseCode = "200", description = "User profile updated")
    @ApiResponse(responseCode = "400", description = "Validation failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid API key")
    public ResponseEntity<UserResponse> updateCurrentUser(@AuthenticationPrincipal User user,
                                                           @Valid @RequestBody UpdateUserRequest request) {
        log.info("User profile update: userId={}", user.getId());
        User updated = userService.updateUser(user, request);
        return ResponseEntity.ok(UserResponse.from(updated));
    }

    @PostMapping("/me/regenerate-key")
    @Operation(summary = "Regenerate API key",
            description = "Generates a new API key and invalidates the old one. " +
                    "The new key is returned in the response — this is the only time it will be shown.")
    @ApiResponse(responseCode = "200", description = "API key regenerated")
    @ApiResponse(responseCode = "401", description = "Missing or invalid API key")
    public ResponseEntity<ApiKeyResponse> regenerateKey(@AuthenticationPrincipal User user) {
        log.info("API key regeneration requested: userId={}", user.getId());
        String newKey = userService.regenerateApiKey(user);
        return ResponseEntity.ok(new ApiKeyResponse(newKey,
                "Your previous API key has been invalidated. Update your Home Assistant configuration."));
    }
}
