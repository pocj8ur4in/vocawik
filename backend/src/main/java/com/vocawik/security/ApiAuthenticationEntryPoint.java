package com.vocawik.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vocawik.web.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Handles authentication failures and writes a standardized JSON response. (401)
 *
 * <p>Used by when a request requires authentication but no valid authentication is present.
 */
@Slf4j
@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    /**
     * Creates an authentication entry point with an isolated {@link ObjectMapper} instance.
     *
     * @param objectMapper base mapper used to serialize {@link ErrorResponse}
     */
    public ApiAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy();
    }

    /**
     * Writes a 401 Unauthorized JSON error response.
     *
     * @param request current HTTP request
     * @param response current HTTP response
     * @param authException thrown authentication exception
     * @throws IOException if writing the response body fails
     */
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        logger.warn("Unauthorized request: {}", authException.getMessage());
        writeErrorResponse(response, HttpStatus.UNAUTHORIZED, "Authentication required.");
    }

    private void writeErrorResponse(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(status, message));
    }
}
