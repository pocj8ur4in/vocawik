package com.vocawik.module.web.locale;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

class AcceptLanguageRequestLocaleResolverTest {

    private final AcceptLanguageRequestLocaleResolver resolver =
            new AcceptLanguageRequestLocaleResolver(
                    Locale.ENGLISH,
                    List.of(Locale.KOREAN, Locale.ENGLISH, Locale.JAPANESE, Locale.CHINESE));

    @Test
    @DisplayName("Should resolve locale from Accept-Language")
    void resolve_withAcceptLanguageHeader_shouldUseMatchedLocale() {
        MockHttpServletRequest request = request();
        request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "ko-KR,ko;q=0.9,en;q=0.8");

        Locale locale = resolver.resolve(request);

        assertThat(locale).isEqualTo(Locale.KOREAN);
    }

    @Test
    @DisplayName("Should use default locale when Accept-Language is missing")
    void resolve_withoutAcceptLanguageHeader_shouldUseDefaultLocale() {
        Locale locale = resolver.resolve(request());

        assertThat(locale).isEqualTo(Locale.ENGLISH);
    }

    @Test
    @DisplayName("Should use default locale when Accept-Language is unsupported")
    void resolve_withUnsupportedAcceptLanguageHeader_shouldUseDefaultLocale() {
        MockHttpServletRequest request = request();
        request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "fr-FR,fr;q=0.9");

        Locale locale = resolver.resolve(request);

        assertThat(locale).isEqualTo(Locale.ENGLISH);
    }

    @Test
    @DisplayName("Should use default locale when Accept-Language is malformed")
    void resolve_withMalformedAcceptLanguageHeader_shouldUseDefaultLocale() {
        MockHttpServletRequest request = request();
        request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "ko;q=abc");

        Locale locale = resolver.resolve(request);

        assertThat(locale).isEqualTo(Locale.ENGLISH);
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/api/v1/users");
    }
}
