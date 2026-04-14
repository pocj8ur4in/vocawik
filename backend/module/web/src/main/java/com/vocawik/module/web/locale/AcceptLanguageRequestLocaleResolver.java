package com.vocawik.module.web.locale;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.HttpHeaders;

/** Resolves request locale from the Accept-Language header. */
public class AcceptLanguageRequestLocaleResolver implements RequestLocaleResolver {

    private final Locale defaultLocale;
    private final List<Locale> supportedLocales;

    /**
     * Creates an Accept-Language based locale resolver.
     *
     * @param defaultLocale locale used when no supported request locale is found
     * @param supportedLocales supported locales
     */
    public AcceptLanguageRequestLocaleResolver(
            Locale defaultLocale, List<Locale> supportedLocales) {
        this.defaultLocale = Objects.requireNonNull(defaultLocale);
        this.supportedLocales = List.copyOf(Objects.requireNonNull(supportedLocales));
    }

    /**
     * Resolves the request locale from Accept-Language or falls back to the default locale.
     *
     * @param request current HTTP request
     * @return resolved locale
     */
    @Override
    public Locale resolve(HttpServletRequest request) {
        String headerValue = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE);
        if (headerValue == null || headerValue.isBlank()) {
            return defaultLocale;
        }

        try {
            List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(headerValue);
            Locale matched = Locale.lookup(ranges, supportedLocales);
            return matched == null ? defaultLocale : matched;
        } catch (IllegalArgumentException ignored) {
            return defaultLocale;
        }
    }
}
