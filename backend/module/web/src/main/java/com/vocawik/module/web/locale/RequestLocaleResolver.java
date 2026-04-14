package com.vocawik.module.web.locale;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;

/** Resolves the effective locale for an HTTP request. */
public interface RequestLocaleResolver {

    /**
     * Resolves the locale for the current HTTP request.
     *
     * @param request current HTTP request
     * @return resolved locale
     */
    Locale resolve(HttpServletRequest request);
}
