package com.vocawik.service.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

/** User profile payload from Google userinfo endpoint. */
public record GoogleUserInfo(
        String sub,
        String email,
        @JsonProperty("email_verified") Boolean emailVerified,
        String name) {}
