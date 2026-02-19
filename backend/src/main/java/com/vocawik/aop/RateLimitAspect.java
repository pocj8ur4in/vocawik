package com.vocawik.aop;

import com.vocawik.web.ClientIpResolver;
import com.vocawik.web.exception.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Aspect for enforcing rate limiting on methods. */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    private final RedissonClient redissonClient;
    private final ClientIpResolver clientIpResolver;

    /**
     * Creates a rate-limit aspect.
     *
     * @param redissonClient Redisson client used to resolve distributed rate limiters
     * @param clientIpResolver client IP resolver with trusted proxy policy
     */
    public RateLimitAspect(RedissonClient redissonClient, ClientIpResolver clientIpResolver) {
        this.redissonClient = redissonClient;
        this.clientIpResolver = clientIpResolver;
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

        String key = buildRateLimitKey(joinPoint);

        RRateLimiter limiter = redissonClient.getRateLimiter(key);
        limiter.trySetRate(
                RateType.OVERALL, rateLimit.requests(), Duration.ofSeconds(rateLimit.seconds()));

        if (!limiter.tryAcquire()) {
            logger.warn("Rate limit exceeded: {}", key);
            throw new TooManyRequestsException(
                    "Too many requests. Please try again in " + rateLimit.seconds() + " seconds.");
        }

        return joinPoint.proceed();
    }

    private String buildRateLimitKey(ProceedingJoinPoint joinPoint) {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        String endpoint = resolveEndpoint(joinPoint, attrs);
        String clientIp = resolveClientIp(attrs);
        AuthContext authContext = resolveAuthContext();
        String actor = authContext.authenticated ? "user:" + authContext.actor : "ip:" + clientIp;
        return "rate_limit:" + endpoint + ":" + actor + ":" + authContext.authState;
    }

    private String resolveEndpoint(ProceedingJoinPoint joinPoint, ServletRequestAttributes attrs) {
        if (attrs == null) {
            // fallback to method signature like ExContainer.exMethod(..)
            return joinPoint.getSignature().toShortString();
        }
        HttpServletRequest request = attrs.getRequest();
        return request.getMethod() + ":" + request.getRequestURI();
    }

    private String resolveClientIp(ServletRequestAttributes attrs) {
        if (attrs == null) {
            return "unknown";
        }
        return clientIpResolver.resolve(attrs.getRequest());
    }

    private AuthContext resolveAuthContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getPrincipal() == null
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return new AuthContext(false, "anonymous", "anon");
        }
        return new AuthContext(true, String.valueOf(authentication.getPrincipal()), "auth");
    }

    private record AuthContext(boolean authenticated, String actor, String authState) {}
}
