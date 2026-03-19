package com.vocawik.service.captcha;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Turnstile captcha configuration properties. */
@Component
@Getter
public class CaptchaProperties {

    private final boolean enabled;
    private final String secretKey;
    private final String siteverifyUrl;

    public CaptchaProperties(
            @Value("${captcha.turnstile.enabled:false}") boolean enabled,
            @Value("${captcha.turnstile.secret-key:}") String secretKey,
            @Value(
                            "${captcha.turnstile.siteverify-url:https://challenges.cloudflare.com/turnstile/v0/siteverify}")
                    String siteverifyUrl) {
        this.enabled = enabled;
        this.secretKey = secretKey;
        this.siteverifyUrl = siteverifyUrl;
    }
}
