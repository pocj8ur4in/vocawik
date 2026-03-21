package com.vocawik.aop;

import com.vocawik.security.ip.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Aspect for logging HTTP request/response around controller execution. */
@Slf4j
@Aspect
@Component
@Profile({"local", "dev", "test"})
public class LoggingAspect {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

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
     * Wraps controller method execution with logging the incoming request.
     *
     * @param joinPoint the controller method invocation
     * @return the original return value of the controller method
     * @throws Throwable if the controller method throws
     */
    @Around("controllerMethods()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attrs != null ? attrs.getRequest() : null;
        if (request == null) {
            return joinPoint.proceed();
        }
        HttpServletResponse response = attrs != null ? attrs.getResponse() : null;

        String requestId = resolveRequestId(request);
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        String fullUri = query != null ? uri + "?" + query : uri;
        String clientIp = clientIpResolver.resolve(request);
        String headers = collectHeaders(request);

        // keep request ID in MDC so every log line on this request can be correlated.
        MDC.put("requestId", requestId);
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
                    ex.getClass().getSimpleName(),
                    ex.getMessage(),
                    elapsed,
                    ex);
            throw ex;
        } finally {
            MDC.remove("requestId");
        }
    }

    /**
     * Returns the HTTP status to log, preferring the servlet response and then a {@link
     * ResponseEntity} result.
     *
     * @param result the controller return value
     * @param response the current servlet response, if available
     * @return the HTTP status code to include in the response log
     */
    private int extractStatus(Object result, HttpServletResponse response) {
        if (response != null && response.getStatus() > 0) {
            return response.getStatus();
        }
        if (result instanceof ResponseEntity<?> responseEntity) {
            return responseEntity.getStatusCode().value();
        }

        // default to 200 OK when no explicit status is set.
        return HttpServletResponse.SC_OK;
    }

    /**
     * Collects request headers into a single string for request logging like {Authorization=...,
     * User-Agent=...}
     *
     * @param request the current HTTP request
     * @return the request headers formatted as {@code {name=value, ...}}
     */
    private String collectHeaders(HttpServletRequest request) {
        List<String> values = new ArrayList<>();
        Enumeration<String> names = request.getHeaderNames();

        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            values.add(name + "=" + request.getHeader(name));
        }
        if (values.isEmpty()) {
            return "{}";
        }
        return values.stream().collect(Collectors.joining(", ", "{", "}"));
    }

    /**
     * Returns the request correlation ID from the request header, generating one when missing.
     *
     * @param request the current HTTP request
     * @return the request ID to store in MDC for log correlation
     */
    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return requestId.trim();
    }
}
