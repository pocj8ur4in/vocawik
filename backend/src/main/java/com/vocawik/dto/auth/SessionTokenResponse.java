package com.vocawik.dto.auth;

/** Access token payload returned from session-related endpoints. */
public record SessionTokenResponse(String accessToken, String tokenType, long expiresIn) {}
