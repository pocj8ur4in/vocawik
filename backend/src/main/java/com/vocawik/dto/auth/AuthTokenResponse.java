package com.vocawik.dto.auth;

/** Access token response payload. */
public record AuthTokenResponse(String accessToken, String tokenType, long expiresIn) {}
