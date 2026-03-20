package com.vocawik.service.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vocawik.common.auth.AuthProvider;
import com.vocawik.common.i18n.Language;
import com.vocawik.domain.user.User;
import com.vocawik.domain.user.UserAuthProvider;
import com.vocawik.domain.user.UserPvProvider;
import com.vocawik.domain.user.UserRole;
import com.vocawik.domain.user.UserTheme;
import com.vocawik.repository.user.UserAuthProviderRepository;
import com.vocawik.repository.user.UserRepository;
import com.vocawik.service.auth.AuthTokenBundle;
import com.vocawik.service.auth.SessionService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OAuthServiceTest {

    private final GoogleOAuthClient googleOAuthClient = mock(GoogleOAuthClient.class);
    private final OAuthProperties oAuthProperties = mock(OAuthProperties.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserAuthProviderRepository userAuthProviderRepository =
            mock(UserAuthProviderRepository.class);
    private final SessionService sessionService = mock(SessionService.class);

    private OAuthService oAuthService;

    @BeforeEach
    void setUp() {
        oAuthService =
                new OAuthService(
                        googleOAuthClient,
                        oAuthProperties,
                        userRepository,
                        userAuthProviderRepository,
                        sessionService);
    }

    @Test
    @DisplayName("Build authorize URL should include encoded OAuth params")
    void buildGoogleAuthorizeUrl_shouldContainEncodedParams() {
        when(oAuthProperties.getAuthUri())
                .thenReturn("https://accounts.google.com/o/oauth2/v2/auth");
        when(oAuthProperties.getClientId()).thenReturn("google-client-id");
        when(oAuthProperties.getRedirectUri())
                .thenReturn("http://localhost:8080/api/v1/oauth/callbacks/google");

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

    @Test
    @DisplayName("authenticateGoogle should generate unique user+6digit nickname")
    void authenticateGoogle_shouldGenerateUniqueUserSixDigitNickname() {
        when(googleOAuthClient.exchangeCode("code"))
                .thenReturn(
                        new GoogleTokenResponse("google-access-token", "Bearer", 3600L, "", ""));
        when(googleOAuthClient.fetchUserInfo("google-access-token"))
                .thenReturn(
                        new GoogleUserInfo(
                                "provider-user-id", "user@example.com", true, "Google Name"));
        when(userAuthProviderRepository.findByProviderAndProviderUserId(any(), any()))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCaseAndIsDeletedFalse("user@example.com"))
                .thenReturn(Optional.empty());
        when(userRepository.findAllNicknamesByIsDeletedFalse())
                .thenReturn(List.of("user000001", "user000002", "user000003"));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionService.issueTokenBundle(any()))
                .thenReturn(new AuthTokenBundle("access", "refresh", 3600L));

        oAuthService.authenticateGoogle("code");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        String nickname = userCaptor.getValue().getNickname();
        assertThat(nickname).matches("^user\\d{6}$");
        assertThat(List.of("user000001", "user000002", "user000003")).doesNotContain(nickname);
        assertThat(userCaptor.getValue().getEmailVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName(
            "authenticateGoogle should not generate nickname when provider mapping already exists")
    void authenticateGoogle_shouldNotGenerateNicknameWhenMappingExists() {
        User existingUser =
                User.create(
                        "user@example.com",
                        "user000123",
                        Language.UND,
                        ZoneId.of("UTC"),
                        UserTheme.UND,
                        UserPvProvider.UND,
                        UserRole.USER);
        UserAuthProvider mapping =
                UserAuthProvider.link(
                        existingUser, AuthProvider.GOOGLE, "provider-user-id", "user@example.com");

        when(googleOAuthClient.exchangeCode("code"))
                .thenReturn(
                        new GoogleTokenResponse("google-access-token", "Bearer", 3600L, "", ""));
        when(googleOAuthClient.fetchUserInfo("google-access-token"))
                .thenReturn(
                        new GoogleUserInfo(
                                "provider-user-id", "user@example.com", true, "Google Name"));
        when(userAuthProviderRepository.findByProviderAndProviderUserId(any(), any()))
                .thenReturn(Optional.of(mapping));
        when(sessionService.issueTokenBundle(any()))
                .thenReturn(new AuthTokenBundle("access", "refresh", 3600L));

        oAuthService.authenticateGoogle("code");

        verify(userRepository, never()).findAllNicknamesByIsDeletedFalse();
        verify(userRepository, never()).save(any());
    }
}
