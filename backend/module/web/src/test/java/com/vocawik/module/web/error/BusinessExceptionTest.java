package com.vocawik.module.web.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BusinessExceptionTest {

    @Test
    @DisplayName("Should use error code default message")
    void constructor_withErrorCode_shouldUseDefaultMessage() {
        BusinessException exception = new BusinessException(ErrorCode.NOT_FOUND);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(exception).hasMessage("Resource not found.");
    }

    @Test
    @DisplayName("Should use custom message")
    void constructor_withCustomMessage_shouldUseCustomMessage() {
        BusinessException exception = new BusinessException(ErrorCode.FORBIDDEN, "No access.");

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(exception).hasMessage("No access.");
    }
}
