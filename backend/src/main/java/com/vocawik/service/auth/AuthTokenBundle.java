package com.vocawik.service.auth;

/** Issued token bundle for authentication flows. */
public record AuthTokenBundle(String accessToken, String refreshToken, long expiresIn) {}
