package com.vocawik.module.web.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ErrorResponseTest {

    @Test
    @DisplayName("Should create response from common error code")
    void of_withErrorCode_shouldUseCodeFields() {
        ErrorResponse response = ErrorResponse.of(ErrorCode.BAD_REQUEST, "Invalid name.");

        assertThat(response.code()).isEqualTo(400);
        assertThat(response.status()).isEqualTo("BAD_REQUEST");
        assertThat(response.message()).isEqualTo("Invalid name.");
        assertThat(response.timestamp()).isNotNull();
    }
}
