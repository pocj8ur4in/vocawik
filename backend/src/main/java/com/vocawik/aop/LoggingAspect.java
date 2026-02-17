package com.vocawik.aop;

import com.vocawik.web.ClientIpResolver;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Aspect for logging HTTP request/response.
 *
 * <p>Logs the following for every controller method invocation:
 *
 * <ul>
 *   <li><b>Request</b> - HTTP method, URI/query, client IP, headers
 *   <li><b>Response</b> - HTTP status code, execution time (ms)
 *   <li><b>Exception</b> - error message when a controller method throws
 * </ul>
 */
@Slf4j
@Aspect
@Component
public class LoggingAspect {

    private final String requestIdHeader;
    private final ClientIpResolver clientIpResolver;

    /**
     * Creates a logging aspect with runtime-configurable HTTP logging policies.
     *
     * @param requestIdHeader header name used for request correlation ID
     * @param clientIpResolver client IP resolver with trusted proxy policy
     */
    public LoggingAspect(
            @Value("${logging.http.request-id-header:X-Request-Id}") String requestIdHeader,
            ClientIpResolver clientIpResolver) {
        this.requestIdHeader = requestIdHeader;
        this.clientIpResolver = clientIpResolver;
    }

    /** Matches all public methods in classes annotated with {@code @RestController}. */
    @Pointcut(
            "within(@org.springframework.web.bind.annotation.RestController *)"
                    + " && execution(public * *(..))")
    public void controllerMethods() {}

    /**
     * Wraps controller method execution with request/response logging.
     *
     * <ul>
     *   <li>uses trusted-proxy policy for client IP extraction
     *   <li>adds/removes request correlation ID to/from MDC
     * </ul>
     *
     * @param joinPoint the method invocation join point
     * @return the original return value of the controller method
     * @throws Throwable if the underlying method throws
     */
    @Around("controllerMethods()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attrs = getCurrentRequestAttributes();
        HttpServletRequest request = attrs != null ? attrs.getRequest() : null;
        if (request == null) {
            return joinPoint.proceed();
        }
        HttpServletResponse response = attrs != null ? attrs.getResponse() : null;

        String method = request.getMethod();
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        String headers = collectHeaders(request);
        String clientIp = clientIpResolver.resolve(request);
        String fullUri = query != null ? uri + "?" + query : uri;
        String requestId = resolveRequestId(request);

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
                    "<<< {} {} | Exception: {} | {}ms",
                    method,
                    fullUri,
                    ex.getMessage(),
                    elapsed,
                    ex);
            throw ex;
        } finally {
            MDC.remove("requestId");
        }
    }

    private ServletRequestAttributes getCurrentRequestAttributes() {
        return (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    }

    int extractStatus(Object result, HttpServletResponse response) {
        if (response != null && response.getStatus() > 0) {
            return response.getStatus();
        }
        if (result instanceof ResponseEntity<?> responseEntity) {
            return responseEntity.getStatusCode().value();
        }
        return HttpServletResponse.SC_OK;
    }

    String collectHeaders(HttpServletRequest request) {
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

    String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(requestIdHeader);
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return requestId.trim();
    }
}
