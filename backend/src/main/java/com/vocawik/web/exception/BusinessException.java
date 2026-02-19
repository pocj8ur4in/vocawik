package com.vocawik.web.exception;

import com.vocawik.web.error.ErrorCode;
import lombok.Getter;

/**
 * Base exception for business logic errors.
 *
 * <p>Carries an {@link ErrorCode} that determines the HTTP status and default message. Throw this
 * (or a subclass) from service layer code.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * Creates a new exception with the error code's default message.
     *
     * @param errorCode the error code
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * Creates a new exception with a custom message.
     *
     * @param errorCode the error code
     * @param message custom detail message
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
