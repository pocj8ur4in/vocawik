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
import org.springframework.security.authentication.InsufficientAuthenticationException;

class ApiAuthenticationEntryPointTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private final ApiAuthenticationEntryPoint entryPoint =
            new ApiAuthenticationEntryPoint(new SecurityErrorResponseWriter(objectMapper));

    @Test
    @DisplayName("Should write unauthorized error response")
    void commence_shouldWriteUnauthorizedResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(
                new MockHttpServletRequest(),
                response,
                new InsufficientAuthenticationException("Missing authentication."));

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");

        JsonNode error = body.get("error");
        assertThat(error.get("code").asInt()).isEqualTo(401);
        assertThat(error.get("status").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(error.get("message").asText()).isEqualTo("Authentication required.");
        assertThat(error.get("details").isArray()).isTrue();
        assertThat(error.get("details").size()).isEqualTo(1);
        assertThat(error.get("timestamp").asText()).isNotBlank();
    }
}
