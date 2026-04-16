package com.vocawik.module.security.jwt;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * Properties for JWT creation and validation.
 *
 * @param secret Base64-encoded signing secret
 * @param issuer expected token issuer
 * @param audience expected token audience
 * @param accessExpiration access token lifetime
 * @param refreshExpiration refresh token lifetime
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("vocawik") String issuer,
        @DefaultValue("vocawik-api") String audience,
        @DefaultValue("1h") Duration accessExpiration,
        @DefaultValue("30d") Duration refreshExpiration) {

    /**
     * Rejects an incomplete JWT trust contract before token infrastructure is created.
     *
     * @param secret Base64 encoded signing secret
     * @param issuer expected token issuer
     * @param audience expected token audience
     * @param accessExpiration access token lifetime
     * @param refreshExpiration refresh token lifetime
     * @throws IllegalArgumentException if the secret, issuer, or audience is missing or blank
     * @throws NullPointerException if either token lifetime is missing
     */
    public JwtProperties {
        secret = requireText(secret, "secret");
        issuer = requireText(issuer, "issuer");
        audience = requireText(audience, "audience");
        accessExpiration = Objects.requireNonNull(accessExpiration, "accessExpiration");
        refreshExpiration = Objects.requireNonNull(refreshExpiration, "refreshExpiration");
    }

    /**
     * Requires a non-blank text property value.
     *
     * @param value property value
     * @param name property name suffix
     * @return non-blank property value
     * @throws IllegalArgumentException if the property value is missing or blank
     */
    private static String requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("security.jwt." + name + " is required");
        }
        return value;
    }
}
