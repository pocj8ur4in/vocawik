package com.vocawik.module.web.error;

import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Handles controller exceptions and returns consistent HTTP error responses. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles validation errors from request body binding.
     *
     * @param ex validation exception
     * @return bad request response with field error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {
        String message = resolveValidationMessage(ex);

        logger.warn("Validation failed: {}", message);

        return toResponse(ErrorCode.BAD_REQUEST, message);
    }

    /**
     * Handles unreadable request bodies such as malformed JSON or invalid enum values.
     *
     * @param ex unreadable message exception
     * @return bad request response
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex) {
        logger.warn("Unreadable request body: {}", ex.getMessage());

        return toResponse(ErrorCode.BAD_REQUEST, "Malformed request body.");
    }

    /**
     * Handles missing required query parameters.
     *
     * @param ex missing request parameter exception
     * @return bad request response
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException ex) {
        String message = ex.getParameterName() + " parameter is required.";

        logger.warn("Missing request parameter: {}", message);

        return toResponse(ErrorCode.BAD_REQUEST, message);
    }

    /**
     * Handles failed conversion of request parameters or path variables.
     *
     * @param ex method argument type mismatch exception
     * @return bad request response
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException ex) {
        String message = ex.getName() + " has an invalid value.";

        logger.warn("Method argument type mismatch: {}", message);

        return toResponse(ErrorCode.BAD_REQUEST, message);
    }

    /**
     * Handles missing static resources and unmapped routes reported by Spring MVC.
     *
     * @param ex no resource exception
     * @return not found response
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(
            NoResourceFoundException ex) {
        return toResponse(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.message());
    }

    /**
     * Handles exceptions that already declare an HTTP status.
     *
     * @param ex response status exception
     * @return response using the declared HTTP status
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        HttpStatusCode statusCode = ex.getStatusCode();
        String status = resolveStatus(statusCode);
        String message = resolveResponseStatusMessage(ex, status);

        logger.warn("Response status exception [{}]: {}", statusCode.value(), message);

        return ResponseEntity.status(statusCode)
                .body(ErrorResponse.of(statusCode.value(), status, message));
    }

    /**
     * Handles all unexpected exceptions.
     *
     * @param ex unhandled exception
     * @return internal server error response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        logger.error("Unhandled exception: ", ex);

        return toResponse(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.message());
    }

    /**
     * Handles expected application failures.
     *
     * @param ex business exception
     * @return response using the exception's error code
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        ApiErrorCode errorCode = ex.getErrorCode();
        String message = messageOrDefault(ex.getMessage(), errorCode);

        logger.warn("Business exception [{}]: {}", errorCode.status(), message);

        return toResponse(errorCode, message);
    }

    /**
     * Converts a field validation exception into a readable message.
     *
     * @param ex validation exception
     * @return validation message
     */
    private String resolveValidationMessage(MethodArgumentNotValidException ex) {
        String message =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(error -> error.getField() + ": " + error.getDefaultMessage())
                        .collect(Collectors.joining(", "));
        return message.isBlank() ? ErrorCode.BAD_REQUEST.message() : message;
    }

    /**
     * Resolves a response status exception message.
     *
     * @param ex response status exception
     * @param status resolved machine-readable status
     * @return response message
     */
    private String resolveResponseStatusMessage(ResponseStatusException ex, String status) {
        String reason = ex.getReason();
        return reason == null || reason.isBlank() ? status : reason;
    }

    /**
     * Resolves a machine-readable status from a Spring HTTP status code.
     *
     * @param statusCode Spring HTTP status code
     * @return machine-readable status
     */
    private String resolveStatus(HttpStatusCode statusCode) {
        HttpStatus httpStatus = HttpStatus.resolve(statusCode.value());
        return httpStatus == null ? "HTTP_" + statusCode.value() : httpStatus.name();
    }

    /**
     * Uses the provided message when present, or the error code's default message otherwise.
     *
     * @param message candidate message
     * @param errorCode fallback error code
     * @return resolved message
     */
    private String messageOrDefault(String message, ApiErrorCode errorCode) {
        return message == null || message.isBlank() ? errorCode.message() : message;
    }

    /**
     * Builds a response entity for an API error code.
     *
     * @param errorCode API error code
     * @param message response message
     * @return response entity
     */
    private ResponseEntity<ErrorResponse> toResponse(ApiErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.httpStatus().value())
                .body(ErrorResponse.of(errorCode, message));
    }
}
