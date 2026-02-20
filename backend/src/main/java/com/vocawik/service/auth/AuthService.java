package com.vocawik.service.auth;

import com.vocawik.common.auth.AuthProvider;
import com.vocawik.domain.user.User;
import com.vocawik.domain.user.UserAuthProvider;
import com.vocawik.repository.user.UserAuthProviderRepository;
import com.vocawik.repository.user.UserRepository;
import com.vocawik.security.jwt.JwtProvider;
import com.vocawik.web.exception.UnauthorizedException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authentication service for OAuth login and token issuance. */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final GoogleOAuthClient googleOAuthClient;
    private final OAuthProperties oAuthProperties;
    private final UserRepository userRepository;
    private final UserAuthProviderRepository userAuthProviderRepository;
    private final JwtProvider jwtProvider;

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

        String role = user.getRole().name();
        String accessToken = jwtProvider.generateAccessToken(user.getUuid(), role);
        String refreshToken = jwtProvider.generateRefreshToken(user.getUuid(), role);

        return new AuthTokenBundle(
                accessToken, refreshToken, jwtProvider.getAccessExpirationSeconds());
    }

    /**
     * Reissues token bundle from valid refresh token.
     *
     * @param refreshToken refresh token
     * @return reissued token bundle
     */
    public AuthTokenBundle refresh(String refreshToken) {
        if (refreshToken == null || !jwtProvider.validateRefreshToken(refreshToken)) {
            throw new UnauthorizedException("Invalid or missing refresh token.");
        }

        String subject = jwtProvider.getSubject(refreshToken);
        String role = jwtProvider.getRole(refreshToken);
        String accessToken = jwtProvider.generateAccessToken(subject, role);
        String nextRefreshToken = jwtProvider.generateRefreshToken(subject, role);

        return new AuthTokenBundle(
                accessToken, nextRefreshToken, jwtProvider.getAccessExpirationSeconds());
    }

    /**
     * Returns refresh token expiration in seconds.
     *
     * @return refresh token expiration (seconds)
     */
    public long getRefreshExpirationSeconds() {
        return jwtProvider.getRefreshExpirationSeconds();
    }

    private User linkOrCreateGoogleUser(GoogleUserInfo userInfo) {
        User user =
                userRepository
                        .findByEmail(userInfo.email())
                        .orElseGet(
                                () ->
                                        userRepository.save(
                                                User.create(
                                                        userInfo.email(),
                                                        resolveNickname(
                                                                userInfo.name(),
                                                                userInfo.email()))));

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
