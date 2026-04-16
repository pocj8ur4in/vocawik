package com.vocawik.module.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates and validates JWT access and refresh tokens.
 *
 * <p>Tokens are signed with HMAC-SHA and carry issuer, audience, role, and token type claims.
 */
@Slf4j
public final class JwtProvider {

    private static final String TOKEN_TYPE_CLAIM = "typ";
    private static final String ROLE_CLAIM = "role";
    private static final String REFRESH_FAMILY_CLAIM = "fam";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String REFRESH_TOKEN_TYPE = "REFRESH";

    private final SecretKey secretKey;
    private final String issuer;
    private final String audience;
    private final Duration accessExpiration;
    private final Duration refreshExpiration;

    /**
     * Creates a JWT provider from configured token properties.
     *
     * @param properties JWT configuration properties
     */
    public JwtProvider(JwtProperties properties) {
        this.secretKey = Keys.hmacShaKeyFor(decodeBase64Secret(properties.secret()));
        this.issuer = properties.issuer();
        this.audience = properties.audience();
        this.accessExpiration = properties.accessExpiration();
        this.refreshExpiration = properties.refreshExpiration();
    }

    /**
     * Generates an access token with the default user role.
     *
     * @param subject user identifier
     * @return signed JWT access token
     */
    public String generateAccessToken(String subject) {
        return generateAccessToken(subject, "USER");
    }

    /**
     * Generates an access token.
     *
     * @param subject user identifier
     * @param role user role
     * @return signed JWT access token
     */
    public String generateAccessToken(String subject, String role) {
        return generateToken(subject, role, accessExpiration, ACCESS_TOKEN_TYPE, null, null);
    }

    /**
     * Generates a refresh token with generated refresh metadata.
     *
     * @param subject user identifier
     * @return signed JWT refresh token
     */
    public String generateRefreshToken(String subject) {
        return generateRefreshToken(
                subject, "USER", UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }

    /**
     * Generates a refresh token with generated refresh metadata.
     *
     * @param subject user identifier
     * @param role user role
     * @return signed JWT refresh token
     */
    public String generateRefreshToken(String subject, String role) {
        return generateRefreshToken(
                subject, role, UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }

    /**
     * Generates a refresh token with explicit refresh metadata.
     *
     * @param subject user identifier
     * @param role user role
     * @param familyId refresh token family identifier
     * @param tokenId refresh token identifier
     * @return signed JWT refresh token
     */
    public String generateRefreshToken(
            String subject, String role, String familyId, String tokenId) {
        return generateToken(
                subject, role, refreshExpiration, REFRESH_TOKEN_TYPE, tokenId, familyId);
    }

    /**
     * Extracts the subject claim from a token.
     *
     * @param token JWT token
     * @return subject claim
     */
    public String getSubject(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Extracts the role claim from a token.
     *
     * @param token JWT token
     * @return role claim
     */
    public String getRole(String token) {
        return parseClaims(token).get(ROLE_CLAIM, String.class);
    }

    /**
     * Extracts the token identifier claim from a token.
     *
     * @param token JWT token
     * @return token identifier or {@code null}
     */
    public String getTokenId(String token) {
        return parseClaims(token).getId();
    }

    /**
     * Extracts the refresh token family claim from a token.
     *
     * @param token JWT token
     * @return refresh token family identifier or {@code null}
     */
    public String getRefreshFamily(String token) {
        return parseClaims(token).get(REFRESH_FAMILY_CLAIM, String.class);
    }

    /**
     * Returns access token lifetime in seconds.
     *
     * @return access token lifetime in seconds
     */
    public long getAccessExpirationSeconds() {
        return accessExpiration.toSeconds();
    }

    /**
     * Returns refresh token lifetime in seconds.
     *
     * @return refresh token lifetime in seconds
     */
    public long getRefreshExpirationSeconds() {
        return refreshExpiration.toSeconds();
    }

    /**
     * Validates any signed JWT token without checking its type.
     *
     * @param token JWT token
     * @return whether the token is valid
     */
    public boolean validateToken(String token) {
        return parseValidatedToken(token, null).isPresent();
    }

    /**
     * Validates an access token.
     *
     * @param token JWT access token
     * @return whether the token is valid and has access type
     */
    public boolean validateAccessToken(String token) {
        return parseAccessToken(token).isPresent();
    }

    /**
     * Parses and validates an access token into an authenticated principal.
     *
     * @param token JWT access token
     * @return validated principal, or empty when the token or required claims are invalid
     */
    public Optional<AuthPrincipal> parseAccessToken(String token) {
        return parseValidatedToken(token, ACCESS_TOKEN_TYPE)
                .map(claims -> new AuthPrincipal(claims.subject(), claims.role()));
    }

    /**
     * Validates a refresh token.
     *
     * @param token JWT refresh token
     * @return whether the token is valid and has refresh type
     */
    public boolean validateRefreshToken(String token) {
        return parseValidatedToken(token, REFRESH_TOKEN_TYPE).isPresent();
    }

    /**
     * Parses and validates common token claims and optionally enforces a token type.
     *
     * @param token JWT token
     * @param expectedType expected token type or {@code null}
     * @return validated subject and role, or empty when validation fails
     */
    private Optional<ValidatedToken> parseValidatedToken(String token, String expectedType) {
        try {
            Claims claims = parseClaims(token);
            if (!issuer.equals(claims.getIssuer())) {
                logger.warn("Invalid JWT issuer");
                return Optional.empty();
            }
            if (!hasExpectedAudience(claims)) {
                logger.warn("Invalid JWT audience");
                return Optional.empty();
            }

            String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
            if (expectedType != null && !expectedType.equals(tokenType)) {
                logger.warn("Invalid JWT type");
                return Optional.empty();
            }

            Optional<UUID> subject = parseSubject(claims.getSubject());
            if (subject.isEmpty()) {
                logger.warn("Invalid JWT subject");
                return Optional.empty();
            }

            String role = claims.get(ROLE_CLAIM, String.class);
            if (!hasText(role)) {
                logger.warn("Invalid JWT role");
                return Optional.empty();
            }

            if (REFRESH_TOKEN_TYPE.equals(tokenType) && !hasValidRefreshMetadata(claims)) {
                logger.warn("Invalid JWT refresh metadata");
                return Optional.empty();
            }

            return Optional.of(new ValidatedToken(subject.orElseThrow(), role.trim()));
        } catch (ExpiredJwtException ex) {
            logger.warn("Expired JWT token");
        } catch (IllegalArgumentException | JwtException ex) {
            logger.warn("Invalid JWT token ({})", ex.getClass().getSimpleName());
        }
        return Optional.empty();
    }

    /**
     * Generates a signed JWT token.
     *
     * @param subject user identifier
     * @param role user role
     * @param expiration token lifetime
     * @param tokenType token type claim
     * @param tokenId token identifier
     * @param refreshFamilyId refresh token family identifier
     * @return signed JWT token
     */
    private String generateToken(
            String subject,
            String role,
            Duration expiration,
            String tokenType,
            String tokenId,
            String refreshFamilyId) {
        String normalizedSubject = requireSubject(subject).toString();
        String normalizedRole = requireText(role, ROLE_CLAIM);
        String normalizedTokenId = tokenId;
        String normalizedRefreshFamilyId = refreshFamilyId;
        if (REFRESH_TOKEN_TYPE.equals(tokenType)) {
            normalizedTokenId = requireText(tokenId, "jti");
            normalizedRefreshFamilyId = requireText(refreshFamilyId, REFRESH_FAMILY_CLAIM);
        }

        Instant now = Instant.now();
        Instant expiry = now.plus(expiration);

        var builder =
                Jwts.builder()
                        .subject(normalizedSubject)
                        .issuer(issuer)
                        .audience()
                        .add(audience)
                        .and()
                        .claim(ROLE_CLAIM, normalizedRole)
                        .claim(TOKEN_TYPE_CLAIM, tokenType)
                        .issuedAt(Date.from(now))
                        .expiration(Date.from(expiry));

        if (REFRESH_TOKEN_TYPE.equals(tokenType)) {
            builder.id(normalizedTokenId);
            builder.claim(REFRESH_FAMILY_CLAIM, normalizedRefreshFamilyId);
        }

        return builder.signWith(secretKey).compact();
    }

    /**
     * Parses signed JWT claims.
     *
     * @param token JWT token
     * @return parsed claims
     */
    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
    }

    /**
     * Decodes the Base64 signing secret.
     *
     * @param secret Base64-encoded signing secret
     * @return decoded secret bytes
     * @throws IllegalArgumentException if the signing secret is not valid Base64
     */
    private byte[] decodeBase64Secret(String secret) {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "security.jwt.secret must be a valid Base64-encoded value", ex);
        }
    }

    /**
     * Returns whether claims contain the expected audience.
     *
     * @param claims parsed claims
     * @return whether expected audience is present
     */
    private boolean hasExpectedAudience(Claims claims) {
        Collection<String> audiences = claims.getAudience();
        return audiences != null && audiences.contains(audience);
    }

    /**
     * Parses a required UUID subject.
     *
     * @param subject raw subject claim
     * @return parsed UUID, or empty when the subject is missing or malformed
     */
    private Optional<UUID> parseSubject(String subject) {
        if (!hasText(subject)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(subject));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /**
     * Validates refresh token rotation metadata.
     *
     * @param claims parsed token claims
     * @return whether token ID and refresh family claims are present
     */
    private boolean hasValidRefreshMetadata(Claims claims) {
        return hasText(claims.getId()) && hasText(claims.get(REFRESH_FAMILY_CLAIM, String.class));
    }

    /**
     * Requires a UUID subject when issuing a token.
     *
     * @param subject subject to validate
     * @return parsed subject UUID
     * @throws IllegalArgumentException if the subject is missing or malformed
     */
    private UUID requireSubject(String subject) {
        return parseSubject(subject)
                .orElseThrow(() -> new IllegalArgumentException("JWT subject must be a UUID"));
    }

    /**
     * Requires and normalizes a text claim when issuing a token.
     *
     * @param value claim value
     * @param claimName claim name for the failure message
     * @return trimmed claim value
     * @throws IllegalArgumentException if the claim is missing or blank
     */
    private String requireText(String value, String claimName) {
        if (!hasText(value)) {
            throw new IllegalArgumentException("JWT " + claimName + " claim is required");
        }
        return value.trim();
    }

    /**
     * Returns whether a claim contains non-whitespace text.
     *
     * @param value claim value
     * @return whether the claim contains text
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Represents structurally validated authentication claims.
     *
     * @param subject authenticated subject UUID
     * @param role authenticated role
     */
    private record ValidatedToken(UUID subject, String role) {}
}
