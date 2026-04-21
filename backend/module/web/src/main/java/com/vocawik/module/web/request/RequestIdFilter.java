package com.vocawik.module.web.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vocawik.module.web.error.ErrorCode;
import com.vocawik.module.web.error.ErrorResponse;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.http.MediaType;

/** Servlet filter that validates request IDs for HTTP request correlation. */
public class RequestIdFilter implements Filter {

    /** Creates a request ID filter. */
    public RequestIdFilter() {}

    // Request ID names used by response headers and MDC logging patterns.
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";

    private static final String INVALID_REQUEST_ID_MESSAGE = "X-Request-Id must be a UUID.";
    private static final Pattern REQUEST_ID_PATTERN =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final ObjectWriter ERROR_RESPONSE_WRITER =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .writerFor(ErrorResponse.class);

    /**
     * Registers the request ID in MDC and echoes it to the response header.
     *
     * @param request servlet request
     * @param response servlet response
     * @param chain next filter chain
     * @throws IOException if the filter chain or error response serialization or output fails
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
        boolean invalidRequestId = requestId == null;
        String correlationId = invalidRequestId ? newRequestId() : requestId;

        try {
            MDC.put(MDC_REQUEST_ID, correlationId);
            httpResponse.setHeader(REQUEST_ID_HEADER, correlationId);
            if (invalidRequestId) {
                writeBadRequest(httpResponse);
                return;
            }
            chain.doFilter(request, response);
        } finally {
            // Servlet threads are reused, so request-scoped MDC must not survive the request.
            restoreRequestId(previousRequestId);
        }
    }

    /**
     * Resolves a valid request ID from the incoming header.
     *
     * @param request HTTP request
     * @return canonical request ID, or {@code null} when the header is absent or invalid
     */
    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null) {
            return null;
        }

        if (!REQUEST_ID_PATTERN.matcher(requestId).matches()) {
            return null;
        }

        try {
            UUID parsed = UUID.fromString(requestId);
            return parsed.toString().equalsIgnoreCase(requestId) ? parsed.toString() : null;
        } catch (IllegalArgumentException invalidUuid) {
            return null;
        }
    }

    /**
     * Writes the standard bad request response for an invalid incoming request ID.
     *
     * @param response HTTP response
     * @throws IOException if the error response cannot be serialized or written
     */
    private void writeBadRequest(HttpServletResponse response) throws IOException {
        byte[] body =
                ERROR_RESPONSE_WRITER.writeValueAsBytes(
                        ErrorResponse.of(ErrorCode.BAD_REQUEST, INVALID_REQUEST_ID_MESSAGE));
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getOutputStream().write(body);
    }

    /**
     * Generates a server-owned request ID.
     *
     * @return random UUID request ID
     */
    private String newRequestId() {
        return UUID.randomUUID().toString();
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
