package com.vocawik.module.web.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Should handle business exception with error code")
    void handleBusinessException_shouldUseErrorCode() {
        ResponseEntity<ErrorResponse> response =
                handler.handleBusinessException(
                        new BusinessException(ErrorCode.FORBIDDEN, "No access."));
        ErrorResponse body = assertThat(response.getBody()).isNotNull().actual();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(body.code()).isEqualTo(403);
        assertThat(body.status()).isEqualTo("FORBIDDEN");
        assertThat(body.message()).isEqualTo("No access.");
    }

    @Test
    @DisplayName("Should hide unexpected illegal argument details")
    void handleException_shouldHideIllegalArgumentMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleException(
                        new IllegalArgumentException("Internal implementation detail."));
        ErrorResponse body = assertThat(response.getBody()).isNotNull().actual();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(body.status()).isEqualTo("INTERNAL_ERROR");
        assertThat(body.message()).isEqualTo("An unexpected error occurred.");
    }

    @Test
    @DisplayName("Should handle unreadable request body as bad request")
    @SuppressWarnings("null")
    void handleHttpMessageNotReadableException_shouldReturnBadRequest() {
        ResponseEntity<ErrorResponse> response =
                handler.handleHttpMessageNotReadableException(
                        new HttpMessageNotReadableException(
                                "Malformed JSON", mock(HttpInputMessage.class)));
        ErrorResponse body = assertThat(response.getBody()).isNotNull().actual();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body.status()).isEqualTo("BAD_REQUEST");
        assertThat(body.message()).isEqualTo("Malformed request body.");
    }

    @Test
    @DisplayName("Should handle missing request parameter as bad request")
    void handleMissingServletRequestParameterException_shouldReturnBadRequest() {
        ResponseEntity<ErrorResponse> response =
                handler.handleMissingServletRequestParameterException(
                        new MissingServletRequestParameterException("page", "Integer"));
        ErrorResponse body = assertThat(response.getBody()).isNotNull().actual();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body.status()).isEqualTo("BAD_REQUEST");
        assertThat(body.message()).isEqualTo("page parameter is required.");
    }

    @Test
    @DisplayName("Should handle argument type mismatch as bad request")
    @SuppressWarnings("null")
    void handleMethodArgumentTypeMismatchException_shouldReturnBadRequest() {
        ResponseEntity<ErrorResponse> response =
                handler.handleMethodArgumentTypeMismatchException(
                        new MethodArgumentTypeMismatchException(
                                "abc", Integer.class, "page", mock(MethodParameter.class), null));
        ErrorResponse body = assertThat(response.getBody()).isNotNull().actual();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body.status()).isEqualTo("BAD_REQUEST");
        assertThat(body.message()).isEqualTo("page has an invalid value.");
    }

    @Test
    @DisplayName("Should handle response status exception")
    void handleResponseStatusException_shouldUseDeclaredStatus() {
        ResponseEntity<ErrorResponse> response =
                handler.handleResponseStatusException(
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Missing."));
        ErrorResponse body = assertThat(response.getBody()).isNotNull().actual();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(body.status()).isEqualTo("NOT_FOUND");
        assertThat(body.message()).isEqualTo("Missing.");
    }

    @Test
    @DisplayName("Should handle unexpected exception as internal error")
    void handleException_shouldReturnInternalError() {
        ResponseEntity<ErrorResponse> response =
                handler.handleException(new RuntimeException("Boom."));
        ErrorResponse body = assertThat(response.getBody()).isNotNull().actual();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(body.status()).isEqualTo("INTERNAL_ERROR");
        assertThat(body.message()).isEqualTo("An unexpected error occurred.");
    }
}
