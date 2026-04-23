package com.vocawik.module.web.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BusinessExceptionTest {

    @Test
    @DisplayName("Should defer default message resolution to the request handler")
    void constructor_withErrorCode_shouldUseDefaultMessage() {
        BusinessException exception = new BusinessException(ErrorCode.NOT_FOUND);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(exception).hasMessage(null);
        assertThat(exception.getDetails()).isEmpty();
    }

    @Test
    @DisplayName("Should use custom message")
    void constructor_withCustomMessage_shouldUseCustomMessage() {
        BusinessException exception = new BusinessException(ErrorCode.FORBIDDEN, "No access.");

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(exception).hasMessage("No access.");
        assertThat(exception.getDetails()).isEmpty();
    }

    @Test
    @DisplayName("Should use structured detail")
    void constructor_withDetail_shouldUseDetail() {
        BusinessException exception =
                new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "Quiz not found.",
                        List.of(Map.of("resource", "quiz")));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(exception).hasMessage("Quiz not found.");
        assertThat(exception.getDetails().get(0)).containsEntry("resource", "quiz");
    }
}
