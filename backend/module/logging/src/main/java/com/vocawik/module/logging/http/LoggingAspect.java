package com.vocawik.module.logging.http;

import com.vocawik.module.web.clientip.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Aspect for logging HTTP requests and responses around controller execution. */
@Slf4j
@Aspect
@Component
@ConditionalOnProperty(prefix = "logging.http", name = "enabled", havingValue = "true")
public class LoggingAspect {

    private static final String MASKED_VALUE = "[masked]";
    private static final Set<String> SENSITIVE_HEADERS =
            Set.of(
                    "authorization",
                    "cookie",
                    "proxy-authorization",
                    "set-cookie",
                    "x-api-key",
                    "x-auth-token");

    private final ClientIpResolver clientIpResolver;

    /**
     * Creates the aspect with the client IP resolver used to log the effective client IP.
     *
     * @param clientIpResolver client IP resolver with trusted proxy policy
     */
    public LoggingAspect(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    /** Matches all public methods in classes annotated with {@code @RestController}. */
    @Pointcut(
            "within(@org.springframework.web.bind.annotation.RestController *)"
                    + " && execution(public * *(..))")
    public void controllerMethods() {}

    /**
     * Wraps controller method execution with request and response logging.
     *
     * @param joinPoint the controller method invocation
     * @return the original return value of the controller method
     * @throws Throwable if the controller method throws
     */
    @Around("controllerMethods()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes attrs)) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = attrs.getRequest();
        HttpServletResponse response = attrs.getResponse();
        String method = request.getMethod();
        String fullUri = resolveFullUri(request);
        String clientIp = clientIpResolver.resolve(request);
        String headers = collectHeaders(request);

        logger.info(">>> {} {} | IP: {} | Headers: {}", method, fullUri, clientIp, headers);

        long start = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            int status = extractStatus(result, response);

            logger.info("<<< {} {} | Status: {} | {}ms", method, fullUri, status, elapsed);
            return result;
        } catch (Throwable ex) {
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            logger.error(
                    "<<< {} {} | Exception: {} {} | {}ms",
                    method,
                    fullUri,
                    ex.getClass().getName(),
                    ex.getMessage(),
                    elapsed,
                    ex);
            throw ex;
        }
    }

    /**
     * Builds the request URI including the query string when present.
     *
     * @param request HTTP request
     * @return request URI with optional query string
     */
    private String resolveFullUri(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        return query == null || query.isBlank() ? uri : uri + "?" + MASKED_VALUE;
    }

    /**
     * Collects request headers into a single log-friendly string.
     *
     * @param request HTTP request
     * @return formatted header map, with sensitive values masked
     */
    private String collectHeaders(HttpServletRequest request) {
        List<String> values = new ArrayList<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            values.add(name + "=" + formatHeaderValue(name, request));
        }
        if (values.isEmpty()) {
            return "{}";
        }
        return values.stream().collect(Collectors.joining(", ", "{", "}"));
    }

    /**
     * Formats one header value for logging, masking sensitive headers.
     *
     * @param name header name
     * @param request HTTP request
     * @return masked value for sensitive headers, or comma-joined header values
     */
    private String formatHeaderValue(String name, HttpServletRequest request) {
        if (SENSITIVE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
            return MASKED_VALUE;
        }
        List<String> headerValues = Collections.list(request.getHeaders(name));
        return String.join(",", headerValues);
    }

    /**
     * Extracts the response status to log after controller execution.
     *
     * @param result controller return value
     * @param response servlet response, if available
     * @return servlet response status, {@link ResponseEntity} status, or {@code 200}
     */
    private int extractStatus(Object result, HttpServletResponse response) {
        if (result instanceof ResponseEntity<?> responseEntity) {
            return responseEntity.getStatusCode().value();
        }
        if (response != null && response.getStatus() > 0) {
            return response.getStatus();
        }
        return HttpServletResponse.SC_OK;
    }
}
