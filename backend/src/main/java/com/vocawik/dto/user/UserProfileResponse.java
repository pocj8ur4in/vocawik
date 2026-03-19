package com.vocawik.dto.user;

import java.time.LocalDateTime;
import java.util.UUID;

/** Response payload for a public user profile page. */
public record UserProfileResponse(
        UUID userUuid,
        String nickname,
        String email,
        String role,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt,
        long edits) {}
