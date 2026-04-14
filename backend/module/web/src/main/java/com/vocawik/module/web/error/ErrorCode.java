package com.vocawik.module.web.error;

import org.springframework.http.HttpStatus;

/** Common application error codes. */
public enum ErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Invalid request."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found."),
    INTERNAL_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.");

    private final HttpStatus httpStatus;
    private final String reason;
    private final String message;

    /**
     * Creates a common error code.
     *
     * @param httpStatus HTTP status to return
     * @param reason stable business error reason
     * @param message default error message
     */
    ErrorCode(HttpStatus httpStatus, String reason, String message) {
        this.httpStatus = httpStatus;
        this.reason = reason;
        this.message = message;
    }

    /**
     * Returns the HTTP status associated with the error code.
     *
     * @return HTTP status
     */
    public HttpStatus httpStatus() {
        return httpStatus;
    }

    /**
     * Returns the enum name as the machine-readable error status.
     *
     * @return machine-readable error status
     */
    public String status() {
        return name();
    }

    /**
     * Returns the stable business reason for this common error.
     *
     * @return business error reason
     */
    public String reason() {
        return reason;
    }

    /**
     * Returns the common error domain.
     *
     * @return error domain
     */
    public String domain() {
        return "vocawik.common";
    }

    /**
     * Returns the default human-readable error message.
     *
     * @return default error message
     */
    public String message() {
        return message;
    }
}
