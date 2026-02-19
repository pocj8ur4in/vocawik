package com.vocawik.exception;

import com.vocawik.web.error.ErrorResponse;
import com.vocawik.web.exception.BusinessException;
import com.vocawik.web.exception.TooManyRequestsException;
import com.vocawik.web.exception.UnauthorizedException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Global exception handler.
 *
 * <p>Catches exceptions thrown by controllers and returns a consistent {@link ErrorResponse}.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles validation errors from {@code @Valid}.
     *
     * @param ex the validation exception
     * @return 400 Bad Request with field error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {

        String message =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(error -> error.getField() + ": " + error.getDefaultMessage())
                        .collect(Collectors.joining(", "));

        logger.warn("Validation failed: {}", message);

        return ResponseEntity.badRequest().body(ErrorResponse.of(HttpStatus.BAD_REQUEST, message));
    }

    /**
     * Handles illegal argument errors.
     *
     * @param ex the exception
     * @return 400 Bad Request
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex) {

        logger.warn("Illegal argument: {}", ex.getMessage());

        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    /**
     * Handles unauthenticated access attempts.
     *
     * @param ex the exception
     * @return 401 Unauthorized
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedException(UnauthorizedException ex) {

        logger.warn("Unauthorized access: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(HttpStatus.UNAUTHORIZED, ex.getMessage()));
    }

    /**
     * Handles rate limit exceeded errors.
     *
     * @param ex the exception
     * @return 429 Too Many Requests
     */
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequestsException(
            TooManyRequestsException ex) {

        logger.warn("Rate limit exceeded: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ErrorResponse.of(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage()));
    }

    /**
     * Handles business logic exceptions with {@link com.vocawik.web.error.ErrorCode}.
     *
     * @param ex the business exception
     * @return response with HTTP status defined by {@link com.vocawik.web.error.ErrorCode}
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {

        logger.warn("Business exception [{}]: {}", ex.getErrorCode().name(), ex.getMessage());

        return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .body(
                        ErrorResponse.of(
                                ex.getErrorCode().getHttpStatus(),
                                ex.getMessage(),
                                ex.getErrorCode().name()));
    }

    /**
     * Handles requests to non-existent resources.
     *
     * @param ex the exception
     * @return 404 Not Found
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(
            NoResourceFoundException ex) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    /**
     * Catches all unhandled exceptions.
     *
     * @param ex the exception
     * @return 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {

        logger.error("Unhandled exception: ", ex);

        return ResponseEntity.internalServerError()
                .body(
                        ErrorResponse.of(
                                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred."));
    }
}
