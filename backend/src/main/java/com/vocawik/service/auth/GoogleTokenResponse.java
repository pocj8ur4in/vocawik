package com.vocawik.service.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Token response from Google OAuth token endpoint. */
public record GoogleTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Long expiresIn,
        String scope,
        @JsonProperty("id_token") String idToken) {}
