package com.vocawik.dto.user;

import java.time.LocalDateTime;

/** Response payload for the current authenticated user. */
public record UserMeResponse(
        String nickname,
        String langCode,
        String timezone,
        String theme,
        String pvProvider,
        LocalDateTime lastLoginAt) {}
