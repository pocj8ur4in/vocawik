package com.vocawik.module.web.locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

class RequestLocaleFilterTest {

    private final RequestLocaleFilter requestLocaleFilter =
            new RequestLocaleFilter(
                    Locale.ENGLISH,
                    List.of(Locale.KOREAN, Locale.ENGLISH, Locale.JAPANESE, Locale.CHINESE));

    @AfterEach
    void resetLocaleContext() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("Should resolve locale from Accept-Language")
    void doFilter_withAcceptLanguageHeader_shouldUseMatchedLocale() throws Exception {
        MockHttpServletRequest request = request();
        HttpServletResponse response = mock(HttpServletResponse.class);
        AtomicReference<Locale> localeInChain = new AtomicReference<>();
        request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "ko-KR,ko;q=0.9,en;q=0.8");

        requestLocaleFilter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) ->
                        localeInChain.set(LocaleContextHolder.getLocale()));

        assertThat(localeInChain).hasValue(Locale.KOREAN);
        verify(response).setHeader(HttpHeaders.CONTENT_LANGUAGE, "ko");
    }

    @Test
    @DisplayName("Should use default locale when Accept-Language is missing")
    void doFilter_withoutAcceptLanguageHeader_shouldUseDefaultLocale() throws Exception {
        MockHttpServletRequest request = request();
        HttpServletResponse response = mock(HttpServletResponse.class);
        AtomicReference<Locale> localeInChain = new AtomicReference<>();

        requestLocaleFilter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) ->
                        localeInChain.set(LocaleContextHolder.getLocale()));

        assertThat(localeInChain).hasValue(Locale.ENGLISH);
        verify(response).setHeader(HttpHeaders.CONTENT_LANGUAGE, "en");
    }

    @Test
    @DisplayName("Should use default locale when Accept-Language is unsupported")
    void doFilter_withUnsupportedAcceptLanguageHeader_shouldUseDefaultLocale() throws Exception {
        MockHttpServletRequest request = request();
        HttpServletResponse response = mock(HttpServletResponse.class);
        AtomicReference<Locale> localeInChain = new AtomicReference<>();
        request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "fr-FR,fr;q=0.9");

        requestLocaleFilter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) ->
                        localeInChain.set(LocaleContextHolder.getLocale()));

        assertThat(localeInChain).hasValue(Locale.ENGLISH);
        verify(response).setHeader(HttpHeaders.CONTENT_LANGUAGE, "en");
    }

    @Test
    @DisplayName("Should restore previous locale context")
    void doFilter_withExistingLocaleContext_shouldRestoreIt() throws Exception {
        MockHttpServletRequest request = request();
        HttpServletResponse response = mock(HttpServletResponse.class);
        LocaleContextHolder.setLocale(Locale.JAPANESE);

        requestLocaleFilter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) ->
                        assertThat(LocaleContextHolder.getLocale()).isEqualTo(Locale.ENGLISH));

        assertThat(LocaleContextHolder.getLocale()).isEqualTo(Locale.JAPANESE);
    }

    @Test
    @DisplayName("Should restore locale context when chain fails")
    void doFilter_whenChainFails_shouldRestoreLocaleContext() {
        MockHttpServletRequest request = request();
        HttpServletResponse response = mock(HttpServletResponse.class);
        LocaleContextHolder.setLocale(Locale.JAPANESE);

        assertThatThrownBy(
                        () ->
                                requestLocaleFilter.doFilter(
                                        request,
                                        response,
                                        (servletRequest, servletResponse) -> {
                                            throw new ServletException("chain failed");
                                        }))
                .isInstanceOf(ServletException.class)
                .hasMessage("chain failed");
        assertThat(LocaleContextHolder.getLocale()).isEqualTo(Locale.JAPANESE);
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/api/v1/users");
    }
}
