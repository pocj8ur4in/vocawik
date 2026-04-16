package com.vocawik.module.security.jwt;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.function.Consumer;

final class JwtTestTokens {

    private JwtTestTokens() {}

    static String signedToken(String secret, Consumer<JwtBuilder> customizer) {
        JwtBuilder builder =
                Jwts.builder()
                        .issuer("vocawik")
                        .audience()
                        .add("vocawik-api")
                        .and()
                        .issuedAt(new Date())
                        .expiration(Date.from(Instant.now().plusSeconds(3_600)));
        customizer.accept(builder);
        return builder.signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret))).compact();
    }
}
