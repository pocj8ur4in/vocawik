package com.vocawik.module.web.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Should handle validation exception with field details")
    @SuppressWarnings("null")
    void handleValidationException_shouldReturnFieldDetails() {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "email", "must not be blank"));

        ResponseEntity<ErrorResponse> response =
                handler.handleValidationException(
                        new MethodArgumentNotValidException(
                                mock(MethodParameter.class), bindingResult));
        ErrorResponse body = assertThat(response.getBody()).isNotNull().actual();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body.status()).isEqualTo("BAD_REQUEST");
        assertThat(body.message()).isEqualTo("Invalid request.");
        assertThat(body.details().get(1)).containsKey("fieldViolations");
        assertThat(body.details().get(1).get("fieldViolations"))
                .asInstanceOf(LIST)
                .singleElement()
                .satisfies(
                        field -> {
                            Map<?, ?> fieldDetail = (Map<?, ?>) field;
                            assertThat(fieldDetail.get("field")).isEqualTo("email");
                            assertThat(fieldDetail.get("message")).isEqualTo("must not be blank");
                        });
    }

    @Test
    @DisplayName("Should handle business exception with error code")
    void handleBusinessException_shouldUseErrorCode() {
        ResponseEntity<ErrorResponse> response =
                handler.handleBusinessException(
                        new BusinessException(
                                ErrorCode.FORBIDDEN,
                                "No access.",
                                List.of(Map.of("requiredRole", "ADMIN"))));
        ErrorResponse body = assertThat(response.getBody()).isNotNull().actual();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(body.code()).isEqualTo(403);
        assertThat(body.status()).isEqualTo("FORBIDDEN");
        assertThat(body.message()).isEqualTo("No access.");
        assertThat(body.details().get(1)).containsEntry("requiredRole", "ADMIN");
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
        Map<?, ?> violation =
                (Map<?, ?>) ((List<?>) body.details().get(1).get("fieldViolations")).get(0);
        assertThat(violation.get("field")).isEqualTo("page");
        assertThat(violation.get("parameterType")).isEqualTo("Integer");
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
        Map<?, ?> violation =
                (Map<?, ?>) ((List<?>) body.details().get(1).get("fieldViolations")).get(0);
        assertThat(violation.get("field")).isEqualTo("page");
        assertThat(violation.get("expectedType")).isEqualTo("Integer");
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

    @Test
    @DisplayName("Should hide response status exception reason")
    void handleException_withResponseStatusException_shouldHideReason() {
        ResponseEntity<ErrorResponse> response =
                handler.handleException(
                        new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR, "Database connection details."));
        ErrorResponse body = assertThat(response.getBody()).isNotNull().actual();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(body.status()).isEqualTo("INTERNAL_ERROR");
        assertThat(body.message()).isEqualTo("An unexpected error occurred.");
    }

    @Test
    @DisplayName("Should localize default error messages from the request locale")
    void handleException_withKoreanLocale_shouldReturnKoreanMessage() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        try {
            ResponseEntity<ErrorResponse> response =
                    handler.handleException(new RuntimeException());
            ErrorResponse body = assertThat(response.getBody()).isNotNull().actual();

            assertThat(body.message()).isEqualTo("예기치 않은 오류가 발생했습니다.");
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }
}
