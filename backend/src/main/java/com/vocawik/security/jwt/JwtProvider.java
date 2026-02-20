package com.vocawik.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT token provider.
 *
 * <p>Handles creation and validation of access and refresh tokens using HMAC-SHA256.
 */
@Slf4j
@Component
public final class JwtProvider {

    private static final String TOKEN_TYPE_CLAIM = "typ";
    private static final String ROLE_CLAIM = "role";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String REFRESH_TOKEN_TYPE = "REFRESH";

    private final SecretKey secretKey;
    private final String issuer;
    private final String audience;
    private final long accessExpiration;
    private final long refreshExpiration;

    /**
     * Constructs a JwtProvider with the given configuration.
     *
     * @param secret Base64-encoded secret key
     * @param issuer expected JWT issuer
     * @param audience expected JWT audience
     * @param accessExpiration access token expiration in milliseconds
     * @param refreshExpiration refresh token expiration in milliseconds
     */
    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.issuer}") String issuer,
            @Value("${jwt.audience}") String audience,
            @Value("${jwt.access-expiration}") long accessExpiration,
            @Value("${jwt.refresh-expiration}") long refreshExpiration) {
        this.secretKey = Keys.hmacShaKeyFor(decodeBase64Secret(secret));
        this.issuer = issuer;
        this.audience = audience;
        this.accessExpiration = accessExpiration;
        this.refreshExpiration = refreshExpiration;
    }

    /**
     * Generates an access token.
     *
     * @param subject user identifier (UUID as string)
     * @return signed JWT access token
     */
    public String generateAccessToken(String subject) {
        return generateAccessToken(subject, "USER");
    }

    /**
     * Generates an access token.
     *
     * @param subject user identifier (UUID as string)
     * @param role user role (e.g. USER, ADMIN)
     * @return signed JWT access token
     */
    public String generateAccessToken(String subject, String role) {
        return generateToken(subject, role, accessExpiration, ACCESS_TOKEN_TYPE);
    }

    /**
     * Generates a refresh token.
     *
     * @param subject user identifier (UUID as string)
     * @return signed JWT refresh token
     */
    public String generateRefreshToken(String subject) {
        return generateRefreshToken(subject, "USER");
    }

    /**
     * Generates a refresh token.
     *
     * @param subject user identifier (UUID as string)
     * @param role user role (e.g. USER, ADMIN)
     * @return signed JWT refresh token
     */
    public String generateRefreshToken(String subject, String role) {
        return generateToken(subject, role, refreshExpiration, REFRESH_TOKEN_TYPE);
    }

    /**
     * Extracts the subject from a token.
     *
     * @param token JWT token
     * @return subject claim
     */
    public String getSubject(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Extracts role claim from a token.
     *
     * @param token JWT token
     * @return role claim value
     */
    public String getRole(String token) {
        return parseClaims(token).get(ROLE_CLAIM, String.class);
    }

    /**
     * Returns access token expiry in seconds.
     *
     * @return access token expiration (seconds)
     */
    public long getAccessExpirationSeconds() {
        return accessExpiration / 1000;
    }

    /**
     * Returns refresh token expiry in seconds.
     *
     * @return refresh token expiration (seconds)
     */
    public long getRefreshExpirationSeconds() {
        return refreshExpiration / 1000;
    }

    /**
     * Validates a JWT token.
     *
     * @param token JWT token to validate
     * @return {@code true} if the token is valid, otherwise {@code false}
     */
    public boolean validateToken(String token) {
        return validateByType(token, null);
    }

    /**
     * Validates an access token.
     *
     * @param token JWT access token
     * @return {@code true} if the token is valid and has access type
     */
    public boolean validateAccessToken(String token) {
        return validateByType(token, ACCESS_TOKEN_TYPE);
    }

    /**
     * Validates a refresh token.
     *
     * @param token JWT refresh token
     * @return {@code true} if the token is valid and has refresh type
     */
    public boolean validateRefreshToken(String token) {
        return validateByType(token, REFRESH_TOKEN_TYPE);
    }

    private boolean validateByType(String token, String expectedType) {
        try {
            Claims claims = parseClaims(token);
            if (!issuer.equals(claims.getIssuer())) {
                logger.warn("Invalid JWT issuer");
                return false;
            }
            if (!hasExpectedAudience(claims)) {
                logger.warn("Invalid JWT audience");
                return false;
            }
            if (expectedType != null
                    && !expectedType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
                logger.warn("Invalid JWT type");
                return false;
            }
            return true;
        } catch (ExpiredJwtException e) {
            logger.warn("Expired JWT token");
        } catch (JwtException e) {
            logger.warn("Invalid JWT token: {}", e.getMessage());
        }
        return false;
    }

    private String generateToken(String subject, String role, long expirationMs, String tokenType) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(subject)
                .issuer(issuer)
                .audience()
                .add(audience)
                .and()
                .claim(ROLE_CLAIM, role)
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
    }

    private byte[] decodeBase64Secret(String secret) {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "jwt.secret must be a valid Base64-encoded value", e);
        }
    }

    private boolean hasExpectedAudience(Claims claims) {
        Collection<String> audiences = claims.getAudience();
        return audiences != null && audiences.contains(audience);
    }
}
