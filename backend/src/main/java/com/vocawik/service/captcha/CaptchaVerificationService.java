package com.vocawik.service.captcha;

import com.vocawik.security.ip.ClientIpResolver;
import com.vocawik.security.jwt.AuthPrincipal;
import com.vocawik.web.error.ErrorCode;
import com.vocawik.web.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Verifies captcha challenges for protected requests. */
@Service
@RequiredArgsConstructor
public class CaptchaVerificationService {

    private final CaptchaProperties captchaProperties;
    private final TurnstileVerificationClient turnstileVerificationClient;
    private final ClientIpResolver clientIpResolver;

    /** Verifies captcha for all callers when captcha is enabled. */
    @Transactional(readOnly = true)
    public void verifyRequired(String captchaToken, HttpServletRequest request) {
        if (!captchaProperties.isEnabled()) {
            return;
        }
        if (captchaProperties.getSecretKey() == null
                || captchaProperties.getSecretKey().isBlank()) {
            throw new IllegalStateException("captcha.turnstile.secret-key must be configured");
        }
        if (captchaToken == null || captchaToken.isBlank()) {
            throw new BusinessException(ErrorCode.CAPTCHA_REQUIRED);
        }

        String clientIp = request == null ? null : clientIpResolver.resolve(request);
        boolean verified =
                turnstileVerificationClient.verify(
                        captchaProperties.getSiteverifyUrl(),
                        captchaProperties.getSecretKey(),
                        captchaToken,
                        clientIp);
        if (!verified) {
            throw new BusinessException(ErrorCode.CAPTCHA_VERIFICATION_FAILED);
        }
    }

    /** Verifies captcha only for callers that are not authenticated users. */
    @Transactional(readOnly = true)
    public void verifyRequiredForNonUser(String captchaToken, HttpServletRequest request) {
        if (isAuthenticatedUser()) {
            return;
        }
        verifyRequired(captchaToken, request);
    }

    private boolean isAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthPrincipal;
    }
}
