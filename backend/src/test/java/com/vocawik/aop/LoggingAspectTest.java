package com.vocawik.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vocawik.web.ClientIpResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class LoggingAspectTest {

    private LoggingAspect loggingAspect;
    private ProceedingJoinPoint joinPoint;
    private ClientIpResolver clientIpResolver;

    @BeforeEach
    void setUp() {
        clientIpResolver =
                new ClientIpResolver(
                        "127.0.0.1/32,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,::1/128");
        loggingAspect = new LoggingAspect("X-Request-Id", clientIpResolver);
        joinPoint = mock(ProceedingJoinPoint.class);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    @Test
    @DisplayName("Should log and return result for successful request")
    void logAround_successfulRequest_shouldReturnResult() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        when(joinPoint.proceed()).thenReturn("result");

        Object result = loggingAspect.logAround(joinPoint);

        assertThat(result).isEqualTo("result");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    @DisplayName("Should extract status from ResponseEntity result")
    void logAround_responseEntity_shouldExtractStatus() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        when(joinPoint.proceed()).thenReturn(ResponseEntity.status(201).body("created"));

        Object result = loggingAspect.logAround(joinPoint);

        assertThat(result).isInstanceOf(ResponseEntity.class);
        assertThat(((ResponseEntity<?>) result).getStatusCode().value()).isEqualTo(201);
    }

    @Test
    @DisplayName("Should prefer HttpServletResponse status over return type")
    void extractStatus_shouldPreferServletResponse() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(204);

        int status =
                loggingAspect.extractStatus(ResponseEntity.status(201).body("created"), response);

        assertThat(status).isEqualTo(204);
    }

    @Test
    @DisplayName("Should log and rethrow exception from controller")
    void logAround_exception_shouldRethrow() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/fail");
        request.addHeader("X-Request-Id", "req-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        when(joinPoint.proceed()).thenThrow(new RuntimeException("test error"));

        assertThatThrownBy(() -> loggingAspect.logAround(joinPoint))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("test error");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    @DisplayName("Should proceed without logging when no request context")
    void logAround_noRequestContext_shouldProceed() throws Throwable {
        RequestContextHolder.resetRequestAttributes();
        when(joinPoint.proceed()).thenReturn("result");

        Object result = loggingAspect.logAround(joinPoint);

        assertThat(result).isEqualTo("result");
    }

    @Test
    @DisplayName("Should include query string in logged URI")
    void logAround_withQueryString_shouldLogFullUri() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/search");
        request.setQueryString("q=test&page=1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        when(joinPoint.proceed()).thenReturn("result");

        Object result = loggingAspect.logAround(joinPoint);

        assertThat(result).isEqualTo("result");
    }

    @Test
    @DisplayName("Should collect all headers as-is")
    void collectHeaders_shouldIncludeAllHeadersAsIs() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-value");
        request.addHeader("Cookie", "SESSION=abc123");
        request.addHeader("User-Agent", "JUnit");
        request.addHeader("X-Ignored", "logged");

        String headers = loggingAspect.collectHeaders(request);

        assertThat(headers).contains("Authorization=Bearer token-value");
        assertThat(headers).contains("Cookie=SESSION=abc123");
        assertThat(headers).contains("User-Agent=JUnit");
        assertThat(headers).contains("X-Ignored=logged");
    }

    @Test
    @DisplayName("Should use header request ID when present")
    void resolveRequestId_withHeader_shouldUseHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "req-abc");

        String requestId = loggingAspect.resolveRequestId(request);

        assertThat(requestId).isEqualTo("req-abc");
    }

    @Test
    @DisplayName("Should generate request ID when header missing")
    void resolveRequestId_withoutHeader_shouldGenerate() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        String requestId = loggingAspect.resolveRequestId(request);

        assertThat(requestId).isNotBlank();
    }
}
