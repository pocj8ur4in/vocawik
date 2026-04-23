package com.vocawik.module.web.error;

import org.springframework.http.HttpStatus;

/** Common application error codes. */
public enum ErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "error.bad-request"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "error.unauthorized"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "error.forbidden"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "error.not-found"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "error.internal");

    private final HttpStatus httpStatus;
    private final String reason;
    private final String messageKey;

    /**
     * Creates a common error code.
     *
     * @param httpStatus HTTP status to return
     * @param reason stable business error reason
     * @param messageKey message bundle key
     */
    ErrorCode(HttpStatus httpStatus, String reason, String messageKey) {
        this.httpStatus = httpStatus;
        this.reason = reason;
        this.messageKey = messageKey;
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
     * Returns the message bundle key used to resolve a localized message.
     *
     * @return message bundle key
     */
    public String messageKey() {
        return messageKey;
    }
}
