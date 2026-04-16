package com.vocawik.module.security.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vocawik.module.web.error.ErrorCode;
import com.vocawik.module.web.error.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Writes security failures using the API error response format. */
@Component
public class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;

    /**
     * Creates a security error response writer.
     *
     * @param objectMapper base JSON mapper configured by the application
     */
    public SecurityErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    /**
     * Writes an API error response unless the servlet response is already committed.
     *
     * @param response current HTTP response
     * @param errorCode security error code
     * @throws IOException if the response body cannot be written
     */
    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        response.setStatus(errorCode.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(errorCode));
    }
}
