package com.vocawik.module.web.error;

import java.time.Instant;
import org.springframework.http.HttpStatus;

/** Standard HTTP error response body. */
public record ErrorResponse(int code, String status, String message, Instant timestamp) {

    /**
     * Creates an error response using the error code's default message.
     *
     * @param errorCode API error code
     * @return error response
     */
    public static ErrorResponse of(ApiErrorCode errorCode) {
        return of(errorCode, errorCode.message());
    }

    /**
     * Creates an error response using a custom message for the given error code.
     *
     * @param errorCode API error code
     * @param message custom error message
     * @return error response
     */
    public static ErrorResponse of(ApiErrorCode errorCode, String message) {
        return of(errorCode.httpStatus().value(), errorCode.status(), message);
    }

    /**
     * Creates an error response using the HTTP status name as the error status.
     *
     * @param httpStatus HTTP status
     * @param message error message
     * @return error response
     */
    public static ErrorResponse of(HttpStatus httpStatus, String message) {
        return of(httpStatus.value(), httpStatus.name(), message);
    }

    /**
     * Creates an error response using explicit response fields.
     *
     * @param code HTTP status code
     * @param status machine-readable error status
     * @param message error message
     * @return error response
     */
    public static ErrorResponse of(int code, String status, String message) {
        return new ErrorResponse(code, status, message, Instant.now());
    }
}
