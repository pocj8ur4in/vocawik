package com.vocawik.dto.user;

import java.time.LocalDateTime;
import java.util.UUID;

/** Response payload for the current authenticated user. */
public record UserMeResponse(
        UUID userUuid,
        String email,
        String nickname,
        String status,
        String role,
        String langCode,
        String timezone,
        String theme,
        String pvProvider,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
