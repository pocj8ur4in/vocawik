package com.vocawik.service.auth;

import com.vocawik.web.error.ErrorCode;
import com.vocawik.web.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Google OAuth API client for token exchange and user info retrieval. */
@Component
@RequiredArgsConstructor
public class GoogleOAuthClient {

    private final OAuthProperties oAuthProperties;
    private final RestClient restClient = RestClient.create();

    /**
     * Exchanges authorization code for Google access token.
     *
     * @param code authorization code
     * @return token response payload
     */
    public GoogleTokenResponse exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", oAuthProperties.getClientId());
        form.add("client_secret", oAuthProperties.getClientSecret());
        form.add("redirect_uri", oAuthProperties.getRedirectUri());
        form.add("grant_type", "authorization_code");
        try {
            GoogleTokenResponse tokenResponse =
                    restClient
                            .post()
                            .uri(oAuthProperties.getTokenUri())
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(form)
                            .retrieve()
                            .body(GoogleTokenResponse.class);

            if (tokenResponse == null || tokenResponse.accessToken() == null) {
                throw new BusinessException(ErrorCode.OAUTH_TOKEN_EXCHANGE_FAILED);
            }
            return tokenResponse;
        } catch (RestClientException ex) {
            throw new BusinessException(
                    ErrorCode.OAUTH_TOKEN_EXCHANGE_FAILED, "OAuth token exchange failed.");
        }
    }

    /**
     * Fetches Google user profile from userinfo endpoint.
     *
     * @param accessToken google access token
     * @return normalized user profile
     */
    public GoogleUserInfo fetchUserInfo(String accessToken) {
        try {
            GoogleUserInfo userInfo =
                    restClient
                            .get()
                            .uri(oAuthProperties.getUserInfoUri())
                            .header("Authorization", "Bearer " + accessToken)
                            .retrieve()
                            .body(GoogleUserInfo.class);
            if (userInfo == null || userInfo.sub() == null || userInfo.email() == null) {
                throw new BusinessException(ErrorCode.OAUTH_USERINFO_FETCH_FAILED);
            }
            return userInfo;
        } catch (RestClientException ex) {
            throw new BusinessException(
                    ErrorCode.OAUTH_USERINFO_FETCH_FAILED, "OAuth user info fetch failed.");
        }
    }
}
