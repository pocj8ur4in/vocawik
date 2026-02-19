package com.vocawik.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.vocawik.web.error.ErrorCode;
import com.vocawik.web.error.ErrorResponse;
import com.vocawik.web.exception.BusinessException;
import com.vocawik.web.exception.TooManyRequestsException;
import com.vocawik.web.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("Validation failed should return 400")
    void handleValidationException_shouldReturn400() {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "name", "must not be blank"));

        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(400);
        assertThat(response.getBody().message()).contains("name");
    }

    @Test
    @DisplayName("IllegalArgumentException should return 400")
    void handleIllegalArgumentException_shouldReturn400() {
        IllegalArgumentException ex = new IllegalArgumentException("bad argument");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgumentException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("bad argument");
    }

    @Test
    @DisplayName("NoResourceFoundException should return 404")
    void handleNoResourceFoundException_shouldReturn404() throws NoResourceFoundException {
        NoResourceFoundException ex =
                new NoResourceFoundException(
                        org.springframework.http.HttpMethod.GET, "/nonexistent", null);

        ResponseEntity<ErrorResponse> response = handler.handleNoResourceFoundException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(404);
    }

    @Test
    @DisplayName("UnauthorizedException should return 401")
    void handleUnauthorizedException_shouldReturn401() {
        UnauthorizedException ex = new UnauthorizedException("Authentication required.");

        ResponseEntity<ErrorResponse> response = handler.handleUnauthorizedException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(401);
        assertThat(response.getBody().message()).contains("Authentication required");
    }

    @Test
    @DisplayName("TooManyRequestsException should return 429")
    void handleTooManyRequestsException_shouldReturn429() {
        TooManyRequestsException ex =
                new TooManyRequestsException("Too many requests. Please try again in 60 seconds.");

        ResponseEntity<ErrorResponse> response = handler.handleTooManyRequestsException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(429);
        assertThat(response.getBody().message()).contains("Too many requests");
    }

    @Test
    @DisplayName("BusinessException should return status from ErrorCode")
    void handleBusinessException_shouldReturnErrorCodeStatus() {
        BusinessException ex = new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);

        ResponseEntity<ErrorResponse> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(404);
        assertThat(response.getBody().message()).isEqualTo("Resource not found.");
        assertThat(response.getBody().status()).isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    @DisplayName("BusinessException with custom message should use custom message")
    void handleBusinessException_withCustomMessage_shouldUseCustomMessage() {
        BusinessException ex =
                new BusinessException(ErrorCode.FORBIDDEN, "admin@vocawik.com already exists");

        ResponseEntity<ErrorResponse> response = handler.handleBusinessException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(403);
        assertThat(response.getBody().message()).contains("admin@vocawik.com");
        assertThat(response.getBody().status()).isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("Unexpected exception should return 500")
    void handleException_shouldReturn500() {
        Exception ex = new RuntimeException("unexpected error");

        ResponseEntity<ErrorResponse> response = handler.handleException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(500);
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred.");
    }
}
