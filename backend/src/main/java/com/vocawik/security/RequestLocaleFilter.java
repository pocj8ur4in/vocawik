package com.vocawik.security;

import com.vocawik.common.i18n.I18nService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Sets request locale with user preference first, then Accept-Language fallback. */
@Component
@RequiredArgsConstructor
public class RequestLocaleFilter extends OncePerRequestFilter {

    private final I18nService i18nService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Locale locale = i18nService.resolve(request);
        LocaleContextHolder.setLocale(locale);
        response.setHeader(HttpHeaders.CONTENT_LANGUAGE, locale.toLanguageTag());

        try {
            filterChain.doFilter(request, response);
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }
}
