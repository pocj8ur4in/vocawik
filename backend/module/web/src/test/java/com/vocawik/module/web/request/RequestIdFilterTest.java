package com.vocawik.module.web.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter requestIdFilter = new RequestIdFilter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("Should use incoming request ID header")
    void doFilter_withRequestIdHeader_shouldUseHeaderValue() throws Exception {
        MockHttpServletRequest request = request();
        HttpServletResponse response = mock(HttpServletResponse.class);
        AtomicReference<String> requestIdInChain = new AtomicReference<>();
        String requestId = UUID.randomUUID().toString();
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, requestId);

        requestIdFilter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) ->
                        requestIdInChain.set(MDC.get(RequestIdFilter.MDC_REQUEST_ID)));

        assertThat(requestIdInChain).hasValue(requestId);
        verify(response).setHeader(RequestIdFilter.REQUEST_ID_HEADER, requestId);
        assertThat(MDC.get(RequestIdFilter.MDC_REQUEST_ID)).isNull();
    }

    @Test
    @DisplayName("Should reject a missing request ID header")
    void doFilter_withoutRequestIdHeader_shouldReturnBadRequest() throws Exception {
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        requestIdFilter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(request, response);
        assertBadRequestResponse(response, null);
        assertThat(MDC.get(RequestIdFilter.MDC_REQUEST_ID)).isNull();
    }

    @Test
    @DisplayName("Should normalize an uppercase UUID request ID")
    void doFilter_withUppercaseUuid_shouldUseCanonicalValue() throws Exception {
        String requestId = UUID.randomUUID().toString();
        String uppercaseRequestId = requestId.toUpperCase(Locale.ROOT);
        MockHttpServletRequest request = request();
        HttpServletResponse response = mock(HttpServletResponse.class);
        AtomicReference<String> requestIdInChain = new AtomicReference<>();
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, uppercaseRequestId);

        requestIdFilter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) ->
                        requestIdInChain.set(MDC.get(RequestIdFilter.MDC_REQUEST_ID)));

        assertThat(requestIdInChain).hasValue(requestId);
        verify(response).setHeader(RequestIdFilter.REQUEST_ID_HEADER, requestId);
        assertThat(MDC.get(RequestIdFilter.MDC_REQUEST_ID)).isNull();
    }

    @Test
    @DisplayName("Should reject invalid incoming request IDs")
    void doFilter_withInvalidRequestIdHeader_shouldReturnBadRequest() throws Exception {
        List<String> invalidRequestIds =
                List.of(
                        "",
                        "   ",
                        " " + UUID.randomUUID() + " ",
                        "a".repeat(65),
                        "request-123",
                        "1-1-1-1-1",
                        "request id",
                        "request\nforged-log",
                        "request:colon",
                        "요청-아이디");

        for (String invalidRequestId : invalidRequestIds) {
            MockHttpServletRequest request = request();
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain filterChain = mock(FilterChain.class);
            request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, invalidRequestId);

            requestIdFilter.doFilter(request, response, filterChain);

            verify(filterChain, never()).doFilter(request, response);
            assertBadRequestResponse(response, invalidRequestId);
            assertThat(MDC.get(RequestIdFilter.MDC_REQUEST_ID)).isNull();
        }
    }

    @Test
    @DisplayName("Should expose the server request ID while rejecting a missing header")
    void doFilter_withoutRequestIdHeader_shouldUseServerRequestIdWhileWritingResponse()
            throws Exception {
        MockHttpServletRequest request = request();
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream outputStream = mock(ServletOutputStream.class);
        FilterChain filterChain = mock(FilterChain.class);
        AtomicReference<String> requestIdWhileWriting = new AtomicReference<>();
        ArgumentCaptor<String> responseRequestId = ArgumentCaptor.forClass(String.class);
        MDC.put(RequestIdFilter.MDC_REQUEST_ID, "outer-request");
        doAnswer(
                        invocation -> {
                            requestIdWhileWriting.set(MDC.get(RequestIdFilter.MDC_REQUEST_ID));
                            return null;
                        })
                .when(outputStream)
                .write(any(byte[].class));
        org.mockito.Mockito.when(response.getOutputStream()).thenReturn(outputStream);

        requestIdFilter.doFilter(request, response, filterChain);

        verify(response)
                .setHeader(eq(RequestIdFilter.REQUEST_ID_HEADER), responseRequestId.capture());
        assertThat(requestIdWhileWriting).hasValue(responseRequestId.getValue());
        assertThat(UUID.fromString(responseRequestId.getValue()).toString())
                .isEqualTo(responseRequestId.getValue());
        verify(filterChain, never()).doFilter(request, response);
        assertThat(MDC.get(RequestIdFilter.MDC_REQUEST_ID)).isEqualTo("outer-request");
    }

    @Test
    @DisplayName("Should restore MDC request ID when rejection response writing fails")
    void doFilter_whenRejectionResponseWritingFails_shouldRestoreMdcRequestId() throws Exception {
        MockHttpServletRequest request = request();
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream outputStream = mock(ServletOutputStream.class);
        FilterChain filterChain = mock(FilterChain.class);
        MDC.put(RequestIdFilter.MDC_REQUEST_ID, "outer-request");
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "request-123");
        org.mockito.Mockito.when(response.getOutputStream()).thenReturn(outputStream);
        doThrow(new IOException("response write failed"))
                .when(outputStream)
                .write(any(byte[].class));

        assertThatThrownBy(() -> requestIdFilter.doFilter(request, response, filterChain))
                .isInstanceOf(IOException.class)
                .hasMessage("response write failed");
        verify(filterChain, never()).doFilter(request, response);
        assertThat(MDC.get(RequestIdFilter.MDC_REQUEST_ID)).isEqualTo("outer-request");
    }

    @Test
    @DisplayName("Should restore previous MDC request ID")
    void doFilter_withExistingMdcRequestId_shouldRestoreIt() throws Exception {
        MockHttpServletRequest request = request();
        HttpServletResponse response = mock(HttpServletResponse.class);
        MDC.put(RequestIdFilter.MDC_REQUEST_ID, "outer-request");
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, UUID.randomUUID().toString());

        requestIdFilter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) ->
                        assertThat(MDC.get(RequestIdFilter.MDC_REQUEST_ID))
                                .isNotEqualTo("outer-request"));

        assertThat(MDC.get(RequestIdFilter.MDC_REQUEST_ID)).isEqualTo("outer-request");
    }

    @Test
    @DisplayName("Should restore MDC request ID when chain fails")
    void doFilter_whenChainFails_shouldRestoreMdcRequestId() {
        MockHttpServletRequest request = request();
        HttpServletResponse response = mock(HttpServletResponse.class);
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, UUID.randomUUID().toString());

        assertThatThrownBy(
                        () ->
                                requestIdFilter.doFilter(
                                        request,
                                        response,
                                        (servletRequest, servletResponse) -> {
                                            throw new ServletException("chain failed");
                                        }))
                .isInstanceOf(ServletException.class)
                .hasMessage("chain failed");
        assertThat(MDC.get(RequestIdFilter.MDC_REQUEST_ID)).isNull();
    }

    @Test
    @DisplayName("Should restore MDC request ID when response header fails")
    void doFilter_whenResponseHeaderFails_shouldRestoreMdcRequestId() {
        MockHttpServletRequest request = request();
        HttpServletResponse response = mock(HttpServletResponse.class);
        MDC.put(RequestIdFilter.MDC_REQUEST_ID, "outer-request");
        String requestId = UUID.randomUUID().toString();
        doThrow(new IllegalArgumentException("invalid response header"))
                .when(response)
                .setHeader(eq(RequestIdFilter.REQUEST_ID_HEADER), eq(requestId));
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, requestId);

        assertThatThrownBy(
                        () ->
                                requestIdFilter.doFilter(
                                        request, response, (servletRequest, servletResponse) -> {}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid response header");
        assertThat(MDC.get(RequestIdFilter.MDC_REQUEST_ID)).isEqualTo("outer-request");
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/api/v1/users");
    }

    private void assertBadRequestResponse(
            MockHttpServletResponse response, String incomingRequestId) throws Exception {
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");

        String responseRequestId = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertThat(UUID.fromString(responseRequestId).toString()).isEqualTo(responseRequestId);
        assertThat(responseRequestId).isNotEqualTo(incomingRequestId);

        JsonNode root = objectMapper.readTree(response.getContentAsByteArray());
        JsonNode error = root.path("error");
        assertThat(root.size()).isEqualTo(1);
        assertThat(error.size()).isEqualTo(5);
        assertThat(error.path("code").asInt()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(error.path("status").asText()).isEqualTo("BAD_REQUEST");
        assertThat(error.path("message").asText()).isEqualTo("X-Request-Id must be a UUID.");
        assertThat(error.path("details").size()).isEqualTo(1);
        assertThat(error.path("details").path(0).path("reason").asText()).isEqualTo("BAD_REQUEST");
        assertThat(error.path("details").path(0).path("domain").asText())
                .isEqualTo("vocawik.common");
        assertThat(Instant.parse(error.path("timestamp").asText())).isNotNull();
    }
}
