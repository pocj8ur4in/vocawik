package com.vocawik.module.web.error;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Base exception for expected application failures that should become API error responses. */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<Map<String, Object>> details;

    /**
     * Creates an exception with the error code's default message.
     *
     * @param errorCode application error code
     */
    public BusinessException(ErrorCode errorCode) {
        this(errorCode, Objects.requireNonNull(errorCode, "errorCode").message(), List.of());
    }

    /**
     * Creates an exception with a custom message.
     *
     * @param errorCode application error code
     * @param message custom detail message
     */
    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    /**
     * Creates an exception with a custom message and structured error details.
     *
     * @param errorCode API error code
     * @param message custom detail message
     * @param details structured error details safe for clients
     */
    public BusinessException(
            ErrorCode errorCode, String message, List<Map<String, Object>> details) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        this.details = normalizeDetails(details);
    }

    /**
     * Returns the error code used to build the API error response.
     *
     * @return application error code
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * Returns structured error details safe to expose to API clients.
     *
     * @return structured error details
     */
    public List<Map<String, Object>> getDetails() {
        return details.stream().map(Map::copyOf).toList();
    }

    /**
     * Copies detail maps into an immutable list.
     *
     * @param details source detail maps
     * @return immutable detail maps
     */
    private static List<Map<String, Object>> normalizeDetails(List<Map<String, Object>> details) {
        if (details == null || details.isEmpty()) {
            return List.of();
        }
        return details.stream().map(Map::copyOf).toList();
    }
}
