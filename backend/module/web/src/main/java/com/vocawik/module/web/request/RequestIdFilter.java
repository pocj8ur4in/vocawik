package com.vocawik.module.web.request;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;

/** Servlet filter that resolves request IDs for HTTP request correlation. */
public class RequestIdFilter implements Filter {

    /** Creates a request ID filter. */
    public RequestIdFilter() {}

    // Request ID names used by response headers and MDC logging patterns.
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";

    /**
     * Registers the request ID in MDC and echoes it to the response header.
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

        String previousRequestId = MDC.get(MDC_REQUEST_ID);
        String requestId = resolveRequestId(httpRequest);

        MDC.put(MDC_REQUEST_ID, requestId);
        httpResponse.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            chain.doFilter(request, response);
        } finally {
            // Servlet threads are reused, so request-scoped MDC must not survive the request.
            restoreRequestId(previousRequestId);
        }
    }

    /**
     * Resolves the request ID from the incoming header or generates a new one.
     *
     * @param request HTTP request
     * @return resolved request ID
     */
    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return requestId.trim();
    }

    /**
     * Restores the MDC request ID that was present before the filter ran.
     *
     * @param previousRequestId previous MDC request ID, or null when none existed
     */
    private void restoreRequestId(String previousRequestId) {
        if (previousRequestId == null) {
            MDC.remove(MDC_REQUEST_ID);
            return;
        }
        MDC.put(MDC_REQUEST_ID, previousRequestId);
    }
}
