package com.vocawik.module.web.error;

import org.springframework.http.HttpStatus;

/** Common HTTP error codes. */
public enum ErrorCode implements ApiErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Invalid request."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");

    private final HttpStatus httpStatus;
    private final String message;

    /**
     * Creates a common error code.
     *
     * @param httpStatus HTTP status to return
     * @param message default error message
     */
    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    /**
     * Returns the HTTP status associated with the error code.
     *
     * @return HTTP status
     */
    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }

    /**
     * Returns the enum name as the machine-readable error status.
     *
     * @return machine-readable error status
     */
    @Override
    public String status() {
        return name();
    }

    /**
     * Returns the default human-readable error message.
     *
     * @return default error message
     */
    @Override
    public String message() {
        return message;
    }
}
