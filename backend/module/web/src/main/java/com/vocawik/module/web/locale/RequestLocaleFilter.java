package com.vocawik.module.web.locale;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;

/** Servlet filter that resolves request locale from HTTP headers. */
public class RequestLocaleFilter implements Filter {

    private final Locale defaultLocale;
    private final List<Locale> supportedLocales;

    /**
     * Creates a request locale filter from web locale properties.
     *
     * @param properties locale resolution properties
     */
    public RequestLocaleFilter(WebLocaleProperties properties) {
        this(properties.defaultLocale(), properties.supported());
    }

    /**
     * Creates a request locale filter.
     *
     * @param defaultLocale locale used when no supported request locale is found
     * @param supportedLocales supported locales
     */
    public RequestLocaleFilter(Locale defaultLocale, List<Locale> supportedLocales) {
        this.defaultLocale = Objects.requireNonNull(defaultLocale);
        this.supportedLocales = List.copyOf(Objects.requireNonNull(supportedLocales));
    }

    /**
     * Sets the request locale context and exposes it through the Content-Language header.
     *
     * @param request servlet request
     * @param response servlet response
     * @param chain next filter chain
     * @throws IOException if the filter chain fails with an I/O error
     * @throws ServletException if the filter chain fails with a servlet error
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        LocaleContext previousLocaleContext = LocaleContextHolder.getLocaleContext();
        Locale locale = resolveLocale(httpRequest);

        LocaleContextHolder.setLocale(locale);
        httpResponse.setHeader(HttpHeaders.CONTENT_LANGUAGE, locale.toLanguageTag());

        try {
            chain.doFilter(request, response);
        } finally {
            LocaleContextHolder.setLocaleContext(previousLocaleContext);
        }
    }

    /**
     * Resolves the request locale from Accept-Language or falls back to the default locale.
     *
     * @param request HTTP request
     * @return resolved locale
     */
    private Locale resolveLocale(HttpServletRequest request) {
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
