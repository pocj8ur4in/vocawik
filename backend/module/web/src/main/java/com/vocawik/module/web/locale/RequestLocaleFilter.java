package com.vocawik.module.web.locale;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;

/** Servlet filter that propagates the resolved request locale. */
public class RequestLocaleFilter implements Filter {

    private final RequestLocaleResolver localeResolver;

    /**
     * Creates a request locale filter.
     *
     * @param localeResolver resolver used to select the request locale
     */
    public RequestLocaleFilter(RequestLocaleResolver localeResolver) {
        this.localeResolver = Objects.requireNonNull(localeResolver);
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
        Locale locale = localeResolver.resolve(httpRequest);

        LocaleContextHolder.setLocale(locale);
        httpResponse.setHeader(HttpHeaders.CONTENT_LANGUAGE, locale.toLanguageTag());

        try {
            chain.doFilter(request, response);
        } finally {
            LocaleContextHolder.setLocaleContext(previousLocaleContext);
        }
    }
}
