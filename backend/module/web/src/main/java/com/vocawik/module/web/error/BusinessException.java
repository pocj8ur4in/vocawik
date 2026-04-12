package com.vocawik.module.web.error;

import java.util.Objects;

/** Base exception for expected application failures that should become API error responses. */
public class BusinessException extends RuntimeException {

    private final ApiErrorCode errorCode;

    /**
     * Creates an exception with the error code's default message.
     *
     * @param errorCode API error code
     */
    public BusinessException(ApiErrorCode errorCode) {
        this(errorCode, Objects.requireNonNull(errorCode, "errorCode").message());
    }

    /**
     * Creates an exception with a custom message.
     *
     * @param errorCode API error code
     * @param message custom detail message
     */
    public BusinessException(ApiErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    /**
     * Returns the error code used to build the API error response.
     *
     * @return API error code
     */
    public ApiErrorCode getErrorCode() {
        return errorCode;
    }
}
