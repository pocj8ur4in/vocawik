package com.vocawik.module.web.error;

import com.vocawik.module.web.i18n.ErrorMessageResolver;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Handles controller exceptions and returns consistent HTTP error responses. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ErrorMessageResolver errorMessageResolver;

    /** Creates an exception handler using the default message bundles. */
    public GlobalExceptionHandler() {
        this(new ErrorMessageResolver());
    }

    /**
     * Creates an exception handler using the localized message resolver.
     *
     * @param errorMessageResolver localized error message resolver
     */
    @Autowired
    public GlobalExceptionHandler(ErrorMessageResolver errorMessageResolver) {
        this.errorMessageResolver = errorMessageResolver;
    }

    /**
     * Handles validation errors from request body binding.
     *
     * @param ex validation exception
     * @return bad request response with field error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {
        List<Map<String, String>> fields = resolveValidationFields(ex);
        List<Map<String, Object>> details =
                fields.isEmpty() ? List.of() : List.of(Map.of("fieldViolations", fields));

        logger.warn("Validation failed: {}", fields);

        return toResponse(ErrorCode.BAD_REQUEST, resolveMessage(ErrorCode.BAD_REQUEST), details);
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
        List<Map<String, Object>> details =
                List.of(
                        Map.of(
                                "fieldViolations",
                                List.of(
                                        Map.of(
                                                "field",
                                                ex.getParameterName(),
                                                "description",
                                                message,
                                                "reason",
                                                "MISSING_PARAMETER",
                                                "parameterType",
                                                ex.getParameterType()))));

        logger.warn("Missing request parameter: {}", message);

        return toResponse(ErrorCode.BAD_REQUEST, message, details);
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
        List<Map<String, Object>> details =
                List.of(
                        Map.of(
                                "fieldViolations",
                                List.of(
                                        Map.of(
                                                "field",
                                                ex.getName(),
                                                "description",
                                                message,
                                                "reason",
                                                "INVALID_PARAMETER_TYPE",
                                                "expectedType",
                                                resolveExpectedType(ex)))));

        logger.warn("Method argument type mismatch: {}", message);

        return toResponse(ErrorCode.BAD_REQUEST, message, details);
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
        return toResponse(ErrorCode.NOT_FOUND, resolveMessage(ErrorCode.NOT_FOUND));
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

        return toResponse(ErrorCode.INTERNAL_ERROR, resolveMessage(ErrorCode.INTERNAL_ERROR));
    }

    /**
     * Handles expected application failures.
     *
     * @param ex business exception
     * @return response using the exception's error code
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        String message = messageOrDefault(ex.getMessage(), errorCode);

        logger.warn("Business exception [{}]: {}", errorCode.status(), message);

        return toResponse(errorCode, message, ex.getDetails());
    }

    /**
     * Converts field validation errors into client-safe structured details.
     *
     * @param ex validation exception
     * @return field error details
     */
    private List<Map<String, String>> resolveValidationFields(MethodArgumentNotValidException ex) {
        return ex.getBindingResult().getFieldErrors().stream()
                .map(this::toValidationField)
                .toList();
    }

    /**
     * Converts a field error into a serializable detail object.
     *
     * @param error field validation error
     * @return field detail map
     */
    private Map<String, String> toValidationField(FieldError error) {
        String message = error.getDefaultMessage();
        return Map.of(
                "field",
                error.getField(),
                "message",
                message == null || message.isBlank()
                        ? resolveMessage(ErrorCode.BAD_REQUEST)
                        : message);
    }

    /**
     * Resolves the expected parameter type for argument mismatch details.
     *
     * @param ex method argument type mismatch exception
     * @return expected parameter type name
     */
    private String resolveExpectedType(MethodArgumentTypeMismatchException ex) {
        Class<?> requiredType = ex.getRequiredType();
        return requiredType == null ? "unknown" : requiredType.getSimpleName();
    }

    /**
     * Uses the provided message when present, or the error code's default message otherwise.
     *
     * @param message candidate message
     * @param errorCode fallback error code
     * @return resolved message
     */
    private String messageOrDefault(String message, ErrorCode errorCode) {
        return message == null || message.isBlank() ? resolveMessage(errorCode) : message;
    }

    /**
     * Resolves a message using the locale selected for the current request.
     *
     * @param errorCode error code
     * @return localized error message
     */
    private String resolveMessage(ErrorCode errorCode) {
        Locale locale = LocaleContextHolder.getLocale();
        return errorMessageResolver.resolve(errorCode, locale);
    }

    /**
     * Builds a response entity for an error code.
     *
     * @param errorCode error code
     * @param message response message
     * @return response entity
     */
    private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.httpStatus().value())
                .body(ErrorResponse.of(errorCode, message));
    }

    /**
     * Builds a response entity for an error code with structured details.
     *
     * @param errorCode error code
     * @param message response message
     * @param details structured error details
     * @return response entity
     */
    private ResponseEntity<ErrorResponse> toResponse(
            ErrorCode errorCode, String message, List<Map<String, Object>> details) {
        return ResponseEntity.status(errorCode.httpStatus().value())
                .body(ErrorResponse.of(errorCode, message, details));
    }
}
