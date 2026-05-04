package com.vocawik.module.security.error;

import com.vocawik.module.web.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/** Handles authorization failures with the standard API error response. */
@Slf4j
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityErrorResponseWriter errorResponseWriter;

    /**
     * Creates an access denied handler.
     *
     * @param errorResponseWriter writer for API security error responses
     */
    public ApiAccessDeniedHandler(SecurityErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    /**
     * Writes a 403 error response when an authenticated principal lacks permission.
     *
     * @param request current HTTP request
     * @param response current HTTP response
     * @param accessDeniedException authorization failure
     * @throws IOException if the response body cannot be written
     */
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        logger.warn("Forbidden request: {}", accessDeniedException.getMessage());

        errorResponseWriter.write(response, ErrorCode.FORBIDDEN);
    }
}
