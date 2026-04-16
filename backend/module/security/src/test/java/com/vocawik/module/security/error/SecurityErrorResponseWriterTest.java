package com.vocawik.module.security.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vocawik.module.web.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityErrorResponseWriterTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private final SecurityErrorResponseWriter writer =
            new SecurityErrorResponseWriter(objectMapper);

    @Test
    @DisplayName("Should write standard API error response")
    void write_shouldWriteStandardErrorResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(response, ErrorCode.UNAUTHORIZED);

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");

        JsonNode error = body.get("error");
        assertThat(error.get("code").asInt()).isEqualTo(401);
        assertThat(error.get("status").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(error.get("message").asText()).isEqualTo("Authentication required.");
        assertThat(error.get("details").isArray()).isTrue();
        assertThat(error.get("details").get(0).get("reason").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(error.get("timestamp").asText()).isNotBlank();
    }
}
