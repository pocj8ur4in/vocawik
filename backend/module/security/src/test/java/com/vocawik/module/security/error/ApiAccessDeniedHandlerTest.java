package com.vocawik.module.security.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

class ApiAccessDeniedHandlerTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private final ApiAccessDeniedHandler handler =
            new ApiAccessDeniedHandler(new SecurityErrorResponseWriter(objectMapper));

    @Test
    @DisplayName("Should write forbidden error response")
    void handle_shouldWriteForbiddenResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(
                new MockHttpServletRequest(),
                response,
                new AccessDeniedException("No permission."));

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");

        JsonNode error = body.get("error");
        assertThat(error.get("code").asInt()).isEqualTo(403);
        assertThat(error.get("status").asText()).isEqualTo("FORBIDDEN");
        assertThat(error.get("message").asText()).isEqualTo("Access denied.");
        assertThat(error.get("details").isArray()).isTrue();
        assertThat(error.get("details").size()).isEqualTo(1);
        assertThat(error.get("timestamp").asText()).isNotBlank();
    }
}
