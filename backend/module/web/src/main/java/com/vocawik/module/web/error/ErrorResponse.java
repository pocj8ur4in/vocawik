package com.vocawik.module.web.error;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Standard HTTP error response using an extensible error envelope.
 *
 * @param error error payload
 */
public record ErrorResponse(ErrorBody error) {

    /**
     * Error payload containing transport status, message, and structured details.
     *
     * @param code HTTP status code
     * @param status machine-readable status
     * @param message human-readable message
     * @param details structured error details
     * @param timestamp response creation timestamp
     */
    public record ErrorBody(
            int code,
            String status,
            String message,
            List<Map<String, Object>> details,
            Instant timestamp) {

        /**
         * Normalizes nullable details to immutable values.
         *
         * @param code HTTP status code
         * @param status machine-readable status
         * @param message human-readable message
         * @param details structured error details
         * @param timestamp response creation timestamp
         */
        public ErrorBody {
            details = normalizeDetails(details);
        }

        /**
         * Returns an immutable snapshot of structured error details.
         *
         * @return immutable structured error details
         */
        @Override
        public List<Map<String, Object>> details() {
            return details.stream().map(Map::copyOf).toList();
        }

        /**
         * Copies detail maps into an immutable list.
         *
         * @param details source detail maps
         * @return immutable detail maps
         */
        private static List<Map<String, Object>> normalizeDetails(
                List<Map<String, Object>> details) {
            if (details == null || details.isEmpty()) {
                return List.of();
            }
            return details.stream().map(Map::copyOf).toList();
        }
    }

    /**
     * Creates an error response using the error code's default message.
     *
     * @param errorCode error code
     * @return error response
     */
    public static ErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, errorCode.message());
    }

    /**
     * Creates an error response using a custom message for the given error code.
     *
     * @param errorCode error code
     * @param message custom message
     * @return error response
     */
    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return of(errorCode, message, List.of());
    }

    /**
     * Creates an error response using a custom message and structured details.
     *
     * @param errorCode error code
     * @param message custom message
     * @param details structured error details
     * @return error response
     */
    public static ErrorResponse of(
            ErrorCode errorCode, String message, List<Map<String, Object>> details) {
        List<Map<String, Object>> allDetails = new ArrayList<>();
        allDetails.add(Map.of("reason", errorCode.reason(), "domain", errorCode.domain()));
        allDetails.addAll(details == null ? List.of() : details);
        return of(errorCode.httpStatus().value(), errorCode.status(), message, allDetails);
    }

    /**
     * Creates an error response using explicit response fields and structured details.
     *
     * @param code HTTP status code
     * @param status machine-readable status
     * @param message error message
     * @param details structured error details
     * @return error response
     */
    private static ErrorResponse of(
            int code, String status, String message, List<Map<String, Object>> details) {
        return new ErrorResponse(new ErrorBody(code, status, message, details, Instant.now()));
    }

    /**
     * Returns the HTTP status code from the nested error payload.
     *
     * @return HTTP status code
     */
    public int code() {
        return error.code();
    }

    /**
     * Returns the status value from the nested error payload.
     *
     * @return nested error status
     */
    public String status() {
        return error.status();
    }

    /**
     * Returns the message value from the nested error payload.
     *
     * @return nested error message
     */
    public String message() {
        return error.message();
    }

    /**
     * Returns structured error details from the nested error payload.
     *
     * @return structured error details
     */
    public List<Map<String, Object>> details() {
        return error.details();
    }

    /**
     * Returns the response creation timestamp from the nested error payload.
     *
     * @return response creation timestamp
     */
    public Instant timestamp() {
        return error.timestamp();
    }
}
