package com.vocawik.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vocawik.security.ip.ClientIpResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
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
    private Logger loggingAspectLogger;
    private TestLogAppender logAppender;
    private Level originalLoggerLevel;

    /** Creates an instance and its test dependencies before each test. */
    @BeforeEach
    void setUp() {
        clientIpResolver =
                new ClientIpResolver(
                        "127.0.0.1/32,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,::1/128");
        loggingAspect = new LoggingAspect(clientIpResolver);
        joinPoint = mock(ProceedingJoinPoint.class);
        loggingAspectLogger = (Logger) LogManager.getLogger(LoggingAspect.class);
        originalLoggerLevel = loggingAspectLogger.getLevel();
        loggingAspectLogger.setLevel(Level.INFO);
        logAppender = new TestLogAppender();
        logAppender.start();
        loggingAspectLogger.addAppender(logAppender);
    }

    /** Clears request and logging context after each test to keep tests isolated. */
    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
        loggingAspectLogger.removeAppender(logAppender);
        loggingAspectLogger.setLevel(originalLoggerLevel);
        logAppender.stop();
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
    @DisplayName("Should log response status from ResponseEntity result")
    void logAround_responseEntity_shouldLogResponseEntityStatus() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(joinPoint.proceed()).thenReturn(ResponseEntity.status(201).body("created"));

        Object result = loggingAspect.logAround(joinPoint);

        assertThat(result).isInstanceOf(ResponseEntity.class);
        assertThat(logAppender.messages()).anyMatch(message -> message.contains("Status: 201"));
    }

    @Test
    @DisplayName("Should log and rethrow exception from controller")
    void logAround_exception_shouldRethrow() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/fail");
        request.addHeader("X-Request-Id", "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        when(joinPoint.proceed()).thenThrow(new RuntimeException("test error"));

        assertThatThrownBy(() -> loggingAspect.logAround(joinPoint))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("test error");
        assertThat(logAppender.messages())
                .anyMatch(message -> message.contains("Exception: RuntimeException test error"));
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
    @DisplayName("Should use header request ID for log correlation when present")
    void logAround_shouldUseHeaderRequestIdWhenPresent() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader("X-Request-Id", "request-abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        when(joinPoint.proceed()).thenReturn("result");

        Object result = loggingAspect.logAround(joinPoint);

        assertThat(result).isEqualTo("result");
        assertThat(logAppender.requestIds()).containsOnly("request-abc");
    }

    @Test
    @DisplayName("Should generate request ID for log correlation when header is missing")
    void logAround_shouldGenerateRequestIdWhenHeaderMissing() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        when(joinPoint.proceed()).thenReturn("result");

        Object result = loggingAspect.logAround(joinPoint);

        assertThat(result).isEqualTo("result");
        assertThat(logAppender.requestIds()).hasSize(2).doesNotContainNull();
        assertThat(logAppender.requestIds().getFirst()).isNotBlank();
    }

    private static final class TestLogAppender extends AbstractAppender {

        private final List<LogEvent> events = new ArrayList<>();

        private TestLogAppender() {
            super(
                    "LoggingAspectTestAppender",
                    null,
                    PatternLayout.createDefaultLayout(),
                    false,
                    Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }

        private List<String> messages() {
            return events.stream()
                    .map(logEvent -> logEvent.getMessage().getFormattedMessage())
                    .toList();
        }

        private List<String> requestIds() {
            return events.stream()
                    .map(
                            logEvent ->
                                    Objects.toString(
                                            logEvent.getContextData().getValue("requestId"), null))
                    .toList();
        }
    }
}
