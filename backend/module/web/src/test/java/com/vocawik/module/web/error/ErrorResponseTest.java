package com.vocawik.module.web.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        assertThat(response.details()).hasSize(1);
        assertThat(response.details().get(0))
                .containsEntry("reason", "BAD_REQUEST")
                .containsEntry("domain", "vocawik.common");
        assertThat(response.timestamp()).isNotNull();
        assertThat(response.error()).isNotNull();
    }

    @Test
    @DisplayName("Should serialize the error envelope and detail array")
    void of_withDetails_shouldSerializeEnvelope() {
        ErrorResponse response =
                ErrorResponse.of(
                        ErrorCode.BAD_REQUEST,
                        "Invalid request.",
                        java.util.List.of(
                                java.util.Map.of(
                                        "reason", "WRONG_DOCUMENTS",
                                        "domain", "vocawik.documents")));
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.get("error").get("code").asInt()).isEqualTo(400);
        assertThat(json.get("error").get("status").asText()).isEqualTo("BAD_REQUEST");
        assertThat(json.get("error").get("details").get(1).get("reason").asText())
                .isEqualTo("WRONG_DOCUMENTS");
    }
}
