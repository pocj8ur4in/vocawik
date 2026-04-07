package com.vocawik.aop;

import com.vocawik.security.internal.InternalApiAuthenticationFilter;
import com.vocawik.security.ip.ClientIpResolver;
import com.vocawik.security.jwt.AuthPrincipal;
import com.vocawik.web.exception.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

/** Aspect for enforcing rate limiting on methods annotated with {@link RateLimit}. */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    private static final String POLICY_KEY_SUFFIX = ":policy";
    private static final String POLICY_LOCK_SUFFIX = ":policy:lock";

    private final ClientIpResolver clientIpResolver;
    private final RedissonClient redissonClient;

    /**
     * Creates the aspect with the client IP resolver and distributed rate limiter client.
     *
     * @param clientIpResolver client IP resolver with trusted proxy policy
     * @param redissonClient Redisson client used to resolve distributed rate limiters
     */
    public RateLimitAspect(ClientIpResolver clientIpResolver, RedissonClient redissonClient) {
        this.clientIpResolver = clientIpResolver;
        this.redissonClient = redissonClient;
    }

    /**
     * Checks rate limit using USER_OR_IP strategy before executing the annotated method.
     *
     * <p>USER_OR_IP strategy: authenticated requests are limited per user, and anonymous requests
     * are limited per client IP. (rate_limit:{HTTP_METHOD}:{URI}:{actor}:{authState})
     *
     * @param joinPoint the method invocation join point
     * @param rateLimit the rate limit annotation
     * @return the original return value of the method
     * @throws Throwable if the underlying method throws or the rate limit is exceeded
     */
    @Around("@annotation(rateLimit)")
    public Object checkRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit)
            throws Throwable {
        if (isInternalRequest()) {
            return joinPoint.proceed();
        }

        String key = buildRateLimitKey(joinPoint);
        RateLimitPolicy expectedPolicy =
                new RateLimitPolicy(rateLimit.requests(), rateLimit.seconds());

        RRateLimiter limiter = redissonClient.getRateLimiter(key);
        syncRateLimitPolicy(key, limiter, expectedPolicy);

        if (!limiter.tryAcquire()) {
            logger.warn("Rate limit exceeded: {}", key);
            throw new TooManyRequestsException(
                    "Too many requests. Please try again in " + rateLimit.seconds() + " seconds.");
        }

        return joinPoint.proceed();
    }

    private boolean isInternalRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return false;
        }
        Object value = attrs.getRequest().getAttribute(InternalApiAuthenticationFilter.INTERNAL_REQUEST_ATTRIBUTE);
        return Boolean.TRUE.equals(value);
    }

    /**
     * Ensures the rate limiter uses the current annotation policy before acquiring a permit.
     *
     * @param key the base rate limiter key
     * @param limiter the distributed rate limiter
     * @param expectedPolicy the current rate limit policy from the annotation
     */
    private void syncRateLimitPolicy(
            String key, RRateLimiter limiter, RateLimitPolicy expectedPolicy) {
        String policyKey = key + POLICY_KEY_SUFFIX;
        String expectedValue = expectedPolicy.asValue();
        RBucket<String> policyBucket = redissonClient.getBucket(policyKey);

        if (expectedValue.equals(policyBucket.get())) {
            return;
        }

        RLock lock = redissonClient.getLock(key + POLICY_LOCK_SUFFIX);
        lock.lock();
        try {
            if (expectedValue.equals(policyBucket.get())) {
                return;
            }

            limiter.setRate(
                    RateType.OVERALL,
                    expectedPolicy.requests(),
                    Duration.ofSeconds(expectedPolicy.seconds()));
            policyBucket.set(expectedValue);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Builds the rate limit key from the current endpoint and caller identity.
     *
     * @param joinPoint the intercepted method invocation
     * @return the rate limit key used to resolve the distributed limiter
     */
    private String buildRateLimitKey(ProceedingJoinPoint joinPoint) {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        String endpoint = resolveEndpoint(joinPoint, attrs);
        String clientIp = resolveClientIp(attrs);
        AuthContext authContext = resolveAuthContext();
        String actor = authContext.authenticated ? "user:" + authContext.actor : "ip:" + clientIp;

        return "rate_limit:" + endpoint + ":" + actor + ":" + authContext.authState;
    }

    /**
     * Resolves the endpoint identifier for the rate limit key.
     *
     * @param joinPoint the intercepted method invocation
     * @param attrs the current servlet request attributes, if available
     * @return the endpoint identifier
     */
    private String resolveEndpoint(ProceedingJoinPoint joinPoint, ServletRequestAttributes attrs) {
        if (attrs == null) {
            return joinPoint.getSignature().toShortString();
        }

        HttpServletRequest request = attrs.getRequest();
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String path = pattern != null ? pattern.toString() : request.getRequestURI();

        return request.getMethod() + ":" + path;
    }

    /**
     * Resolves the client IP used in the rate limit key.
     *
     * @param attrs the current servlet request attributes, if available
     * @return the resolved client IP, or {@code unknown} when unavailable
     */
    private String resolveClientIp(ServletRequestAttributes attrs) {
        if (attrs == null) {
            return "unknown";
        }
        return clientIpResolver.resolve(attrs.getRequest());
    }

    /**
     * Resolves whether the current caller is authenticated and which actor identifier will be used.
     *
     * @return the authentication context for rate limit key generation
     */
    private AuthContext resolveAuthContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getPrincipal() == null
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return new AuthContext(false, "anonymous", "anon");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthPrincipal authPrincipal) {
            return new AuthContext(true, authPrincipal.userUuid().toString(), "auth");
        }
        return new AuthContext(true, authentication.getName(), "auth");
    }

    /** Authentication details used to identify the current caller in the rate limit key. */
    private record AuthContext(boolean authenticated, String actor, String authState) {}

    /** Rate limit policy metadata stored alongside the distributed rate limiter. */
    private record RateLimitPolicy(int requests, int seconds) {

        private String asValue() {
            return requests + ":" + seconds;
        }
    }
}
