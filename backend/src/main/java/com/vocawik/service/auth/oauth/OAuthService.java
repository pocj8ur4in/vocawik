package com.vocawik.service.auth.oauth;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles OAuth provider authorization and callback authentication. */
@Service
@RequiredArgsConstructor
public class OAuthService {

    private final GoogleOAuthClient googleOAuthClient;
    private final OAuthProperties oAuthProperties;
    private final UserRepository userRepository;
    private final UserAuthProviderRepository userAuthProviderRepository;
    private final SessionService sessionService;

    /**
     * Builds Google OAuth authorize URL.
     *
     * @return provider authorize URL
     */
    public String buildGoogleAuthorizeUrl(String state) {
        return oAuthProperties.getAuthUri()
                + "?response_type=code"
                + "&client_id="
                + encode(oAuthProperties.getClientId())
                + "&redirect_uri="
                + encode(oAuthProperties.getRedirectUri())
                + "&scope="
                + encode("openid email profile")
                + "&access_type=offline"
                + "&prompt=consent"
                + "&state="
                + encode(state);
    }

    /**
     * Handles Google OAuth callback and returns issued token bundle.
     *
     * @param code authorization code
     * @return issued tokens
     */
    @Transactional
    public AuthTokenBundle authenticateGoogle(String code) {
        GoogleTokenResponse tokenResponse = googleOAuthClient.exchangeCode(code);
        GoogleUserInfo userInfo = googleOAuthClient.fetchUserInfo(tokenResponse.accessToken());

        User user =
                userAuthProviderRepository
                        .findByProviderAndProviderUserId(AuthProvider.GOOGLE, userInfo.sub())
                        .map(UserAuthProvider::getUser)
                        .orElseGet(() -> linkOrCreateGoogleUser(userInfo));

        user.touchLastLoginAt();
        return sessionService.issueTokenBundle(user);
    }

    private User linkOrCreateGoogleUser(GoogleUserInfo userInfo) {
        User user =
                userRepository
                        .findByEmailIgnoreCaseAndIsDeletedFalse(userInfo.email())
                        .orElseGet(
                                () ->
                                        userRepository.save(
                                                User.create(
                                                        userInfo.email(),
                                                        resolveNickname(
                                                                userInfo.name(), userInfo.email()),
                                                        Language.UND,
                                                        ZoneId.of("UTC"),
                                                        UserTheme.UND,
                                                        UserPvProvider.UND,
                                                        UserRole.USER)));

        UserAuthProvider mapping =
                UserAuthProvider.link(user, AuthProvider.GOOGLE, userInfo.sub(), userInfo.email());
        userAuthProviderRepository.save(mapping);
        return user;
    }

    private String resolveNickname(String name, String email) {
        if (name != null && !name.isBlank()) {
            return truncate(name.trim(), 100);
        }
        int at = email.indexOf('@');
        String localPart = at > 0 ? email.substring(0, at) : email;
        return truncate(localPart, 100);
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
