package com.vocawik.module.security.error;

import com.vocawik.module.web.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/** Handles authentication failures with the standard API error response. */
@Slf4j
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorResponseWriter errorResponseWriter;

    /**
     * Creates an authentication entry point.
     *
     * @param errorResponseWriter writer for API security error responses
     */
    public ApiAuthenticationEntryPoint(SecurityErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    /**
     * Writes a 401 error response when authentication is missing or invalid.
     *
     * @param request current HTTP request
     * @param response current HTTP response
     * @param authException authentication failure
     * @throws IOException if the response body cannot be written
     */
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        logger.warn("Unauthorized request: {}", authException.getMessage());

        errorResponseWriter.write(response, ErrorCode.UNAUTHORIZED);
    }
}
