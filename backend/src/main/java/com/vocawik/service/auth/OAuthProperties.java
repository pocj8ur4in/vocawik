package com.vocawik.service.auth;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** OAuth provider configuration properties. */
@Component
@Getter
public class OAuthProperties {
    private final String clientId;
    private final String clientSecret;
    private final String authUri;
    private final String tokenUri;
    private final String userInfoUri;
    private final String redirectUri;

    /**
     * Creates OAuth properties from configuration values.
     *
     * @param clientId google client id
     * @param clientSecret google client secret
     * @param authUri google authorize endpoint
     * @param tokenUri google token endpoint
     * @param userInfoUri google userinfo endpoint
     * @param redirectUri oauth callback redirect uri
     */
    public OAuthProperties(
            @Value("${oauth.google.client-id:}") String clientId,
            @Value("${oauth.google.client-secret:}") String clientSecret,
            @Value("${oauth.google.auth-uri:https://accounts.google.com/o/oauth2/v2/auth}")
                    String authUri,
            @Value("${oauth.google.token-uri:https://oauth2.googleapis.com/token}") String tokenUri,
            @Value("${oauth.google.user-info-uri:https://openidconnect.googleapis.com/v1/userinfo}")
                    String userInfoUri,
            @Value("${oauth.google.redirect-uri:}") String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.authUri = authUri;
        this.tokenUri = tokenUri;
        this.userInfoUri = userInfoUri;
        this.redirectUri = redirectUri;
    }
}
