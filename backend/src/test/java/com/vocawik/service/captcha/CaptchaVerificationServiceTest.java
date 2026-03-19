package com.vocawik.service.captcha;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vocawik.security.guest.GuestPrincipal;
import com.vocawik.security.ip.ClientIpResolver;
import com.vocawik.security.jwt.AuthPrincipal;
import com.vocawik.web.error.ErrorCode;
import com.vocawik.web.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CaptchaVerificationServiceTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Disabled captcha should skip verification")
    void verifyRequired_shouldSkipWhenDisabled() {
        CaptchaProperties captchaProperties =
                new CaptchaProperties(false, "", "https://example.com/siteverify");
        TurnstileVerificationClient verificationClient = mock(TurnstileVerificationClient.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        CaptchaVerificationService service =
                new CaptchaVerificationService(
                        captchaProperties, verificationClient, clientIpResolver);

        service.verifyRequired(null, mock(HttpServletRequest.class));

        verifyNoInteractions(verificationClient, clientIpResolver);
    }

    @Test
    @DisplayName("Enabled captcha should reject blank token")
    void verifyRequired_shouldRejectBlankToken() {
        CaptchaVerificationService service =
                new CaptchaVerificationService(
                        new CaptchaProperties(true, "secret", "https://example.com/siteverify"),
                        mock(TurnstileVerificationClient.class),
                        mock(ClientIpResolver.class));

        assertThatThrownBy(() -> service.verifyRequired(" ", mock(HttpServletRequest.class)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CAPTCHA_REQUIRED);
    }

    @Test
    @DisplayName("Enabled captcha should reject failed verification")
    void verifyRequired_shouldRejectFailedVerification() {
        CaptchaProperties captchaProperties =
                new CaptchaProperties(true, "secret", "https://example.com/siteverify");
        TurnstileVerificationClient verificationClient = mock(TurnstileVerificationClient.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(clientIpResolver.resolve(request)).thenReturn("203.0.113.10");
        when(verificationClient.verify(
                        "https://example.com/siteverify", "secret", "token", "203.0.113.10"))
                .thenReturn(false);
        CaptchaVerificationService service =
                new CaptchaVerificationService(
                        captchaProperties, verificationClient, clientIpResolver);

        assertThatThrownBy(() -> service.verifyRequired("token", request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CAPTCHA_VERIFICATION_FAILED);
    }

    @Test
    @DisplayName("Authenticated users should skip optional captcha verification")
    void verifyRequiredForNonUser_shouldSkipForAuthenticatedUser() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new AuthPrincipal(UUID.randomUUID(), "USER"), null, List.of()));
        TurnstileVerificationClient verificationClient = mock(TurnstileVerificationClient.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        CaptchaVerificationService service =
                new CaptchaVerificationService(
                        new CaptchaProperties(true, "secret", "https://example.com/siteverify"),
                        verificationClient,
                        clientIpResolver);

        service.verifyRequiredForNonUser(null, mock(HttpServletRequest.class));

        verifyNoInteractions(verificationClient, clientIpResolver);
    }

    @Test
    @DisplayName("Guest callers should still require captcha verification")
    void verifyRequiredForNonUser_shouldVerifyForGuest() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new GuestPrincipal(UUID.randomUUID()), null, List.of()));
        CaptchaProperties captchaProperties =
                new CaptchaProperties(true, "secret", "https://example.com/siteverify");
        TurnstileVerificationClient verificationClient = mock(TurnstileVerificationClient.class);
        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(clientIpResolver.resolve(request)).thenReturn("203.0.113.11");
        when(verificationClient.verify(
                        "https://example.com/siteverify", "secret", "token", "203.0.113.11"))
                .thenReturn(true);
        CaptchaVerificationService service =
                new CaptchaVerificationService(
                        captchaProperties, verificationClient, clientIpResolver);

        service.verifyRequiredForNonUser("token", request);

        verify(verificationClient)
                .verify("https://example.com/siteverify", "secret", "token", "203.0.113.11");
    }
}
