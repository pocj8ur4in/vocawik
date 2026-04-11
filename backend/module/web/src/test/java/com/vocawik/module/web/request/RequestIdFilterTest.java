package com.vocawik.module.web.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;

class RequestIdFilterTest {

    private final RequestIdFilter requestIdFilter = new RequestIdFilter();

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
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, " request-123 ");

        requestIdFilter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) ->
                        requestIdInChain.set(MDC.get(RequestIdFilter.MDC_REQUEST_ID)));

        assertThat(requestIdInChain).hasValue("request-123");
        verify(response).setHeader(RequestIdFilter.REQUEST_ID_HEADER, "request-123");
        assertThat(MDC.get(RequestIdFilter.MDC_REQUEST_ID)).isNull();
    }

    @Test
    @DisplayName("Should generate request ID when header is missing")
    void doFilter_withoutRequestIdHeader_shouldGenerateRequestId() throws Exception {
        MockHttpServletRequest request = request();
        HttpServletResponse response = mock(HttpServletResponse.class);
        AtomicReference<String> requestIdInChain = new AtomicReference<>();
        ArgumentCaptor<String> requestIdCaptor = ArgumentCaptor.forClass(String.class);

        requestIdFilter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) ->
                        requestIdInChain.set(MDC.get(RequestIdFilter.MDC_REQUEST_ID)));

        assertThat(requestIdInChain.get()).isNotBlank();
        verify(response)
                .setHeader(eq(RequestIdFilter.REQUEST_ID_HEADER), requestIdCaptor.capture());
        assertThat(requestIdCaptor.getValue()).isEqualTo(requestIdInChain.get());
        assertThat(MDC.get(RequestIdFilter.MDC_REQUEST_ID)).isNull();
    }

    @Test
    @DisplayName("Should restore previous MDC request ID")
    void doFilter_withExistingMdcRequestId_shouldRestoreIt() throws Exception {
        MockHttpServletRequest request = request();
        HttpServletResponse response = mock(HttpServletResponse.class);
        MDC.put(RequestIdFilter.MDC_REQUEST_ID, "outer-request");

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

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/api/v1/users");
    }
}
