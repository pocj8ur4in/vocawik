package com.vocawik.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vocawik.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Handles access-denied failures and writes a standardized JSON response. (403)
 *
 * <p>Used when an authenticated user is not authorized to access a resource.
 */
@Slf4j
@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /**
     * Creates an access-denied handler with an isolated {@link ObjectMapper} instance.
     *
     * @param objectMapper base mapper used to serialize {@link ErrorResponse}
     */
    public ApiAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy();
    }

    /**
     * Writes a 403 Forbidden JSON error response.
     *
     * @param request current HTTP request
     * @param response current HTTP response
     * @param accessDeniedException thrown authorization exception
     * @throws IOException if writing the response body fails
     */
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        logger.warn("Forbidden request: {}", accessDeniedException.getMessage());
        writeErrorResponse(response, HttpStatus.FORBIDDEN, "Access denied.");
    }

    private void writeErrorResponse(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(status, message));
    }
}
