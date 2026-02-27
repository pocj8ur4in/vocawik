package com.vocawik.service.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vocawik.repository.user.UserAuthProviderRepository;
import com.vocawik.repository.user.UserRepository;
import com.vocawik.service.auth.SessionService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OAuthServiceTest {

    @Test
    @DisplayName("Build authorize URL should include encoded OAuth params")
    void buildGoogleAuthorizeUrl_shouldContainEncodedParams() {
        GoogleOAuthClient googleOAuthClient = mock(GoogleOAuthClient.class);
        OAuthProperties oAuthProperties = mock(OAuthProperties.class);
        when(oAuthProperties.getAuthUri())
                .thenReturn("https://accounts.google.com/o/oauth2/v2/auth");
        when(oAuthProperties.getClientId()).thenReturn("google-client-id");
        when(oAuthProperties.getRedirectUri())
                .thenReturn("http://localhost:8080/api/v1/oauth/callbacks/google");

        OAuthService oAuthService =
                new OAuthService(
                        googleOAuthClient,
                        oAuthProperties,
                        mock(UserRepository.class),
                        mock(UserAuthProviderRepository.class),
                        mock(SessionService.class));

        String state = "state-value";
        String authorizeUrl = oAuthService.buildGoogleAuthorizeUrl(state);

        assertThat(authorizeUrl)
                .startsWith("https://accounts.google.com/o/oauth2/v2/auth?response_type=code");
        assertThat(authorizeUrl)
                .contains(
                        "&client_id="
                                + URLEncoder.encode("google-client-id", StandardCharsets.UTF_8));
        assertThat(authorizeUrl)
                .contains(
                        "&redirect_uri="
                                + URLEncoder.encode(
                                        "http://localhost:8080/api/v1/oauth/callbacks/google",
                                        StandardCharsets.UTF_8));
        assertThat(authorizeUrl)
                .contains(
                        "&scope="
                                + URLEncoder.encode(
                                        "openid email profile", StandardCharsets.UTF_8));
        assertThat(authorizeUrl)
                .contains("&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8));
    }
}
