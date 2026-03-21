package com.vocawik.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vocawik.security.ip.ClientIpResolver;
import com.vocawik.web.exception.TooManyRequestsException;
import java.time.Duration;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

class RateLimitAspectTest {

    private RedissonClient redissonClient;
    private RRateLimiter rateLimiter;
    private RBucket policyBucket;
    private RLock policyLock;
    private ClientIpResolver clientIpResolver;
    private RateLimitAspect aspect;
    private ProceedingJoinPoint joinPoint;
    private RateLimit rateLimit;

    @BeforeEach
    void setUp() {
        redissonClient = mock(RedissonClient.class);
        rateLimiter = mock(RRateLimiter.class);
        policyBucket = mock(RBucket.class);
        policyLock = mock(RLock.class);
        clientIpResolver = mock(ClientIpResolver.class);
        aspect = new RateLimitAspect(clientIpResolver, redissonClient);
        joinPoint = mock(ProceedingJoinPoint.class);
        rateLimit = mock(RateLimit.class);

        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(redissonClient.getBucket(anyString())).thenReturn(policyBucket);
        when(redissonClient.getLock(anyString())).thenReturn(policyLock);
        when(rateLimit.requests()).thenReturn(10);
        when(rateLimit.seconds()).thenReturn(60);
        when(policyBucket.get()).thenReturn(null);

        Signature signature = mock(Signature.class);
        when(signature.toShortString()).thenReturn("TestController.testMethod()");
        when(joinPoint.getSignature()).thenReturn(signature);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        request.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(clientIpResolver.resolve(request)).thenReturn("127.0.0.1");

        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should allow request when rate limit is not exceeded")
    void checkRateLimit_withinLimit_shouldProceed() throws Throwable {
        when(rateLimiter.tryAcquire()).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.checkRateLimit(joinPoint, rateLimit);

        assertThat(result).isEqualTo("ok");
        verify(rateLimiter).setRate(RateType.OVERALL, 10, Duration.ofSeconds(60));
        verify(policyBucket).set("10:60");
    }

    @Test
    @DisplayName("Should throw TooManyRequestsException when rate limit is exceeded")
    void checkRateLimit_exceedsLimit_shouldThrow() {
        when(rateLimiter.tryAcquire()).thenReturn(false);

        assertThatThrownBy(() -> aspect.checkRateLimit(joinPoint, rateLimit))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("Too many requests");
    }

    @Test
    @DisplayName("Should build user_or_ip key using authenticated user")
    void checkRateLimit_withAuthenticatedUser_shouldUseUserKey() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test/123");
        request.setRemoteAddr("127.0.0.1");
        request.setAttribute(
                HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/test/{testId}");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(clientIpResolver.resolve(request)).thenReturn("127.0.0.1");

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("42", "N/A", List.of()));
        when(rateLimiter.tryAcquire()).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.checkRateLimit(joinPoint, rateLimit);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(redissonClient).getRateLimiter(captor.capture());
        assertThat(captor.getValue())
                .isEqualTo("rate_limit:GET:/api/v1/test/{testId}:user:42:auth");
    }

    @Test
    @DisplayName("Should build user_or_ip key using resolved IP for anonymous user")
    void checkRateLimit_withAnonymousUser_shouldUseIpKey() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test/123");
        request.setRemoteAddr("127.0.0.1");
        request.setAttribute(
                HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/test/{testId}");
        request.addHeader("X-Forwarded-For", "203.0.113.50, 70.41.3.18");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(clientIpResolver.resolve(request)).thenReturn("203.0.113.50");

        when(rateLimiter.tryAcquire()).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.checkRateLimit(joinPoint, rateLimit);

        assertThat(result).isEqualTo("ok");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(redissonClient).getRateLimiter(captor.capture());
        assertThat(captor.getValue())
                .isEqualTo("rate_limit:GET:/api/v1/test/{testId}:ip:203.0.113.50:anon");
    }

    @Test
    @DisplayName("Should skip rate limit reset when stored policy already matches")
    void checkRateLimit_matchingPolicy_shouldNotResetLimiter() throws Throwable {
        when(policyBucket.get()).thenReturn("10:60");
        when(rateLimiter.tryAcquire()).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.checkRateLimit(joinPoint, rateLimit);

        assertThat(result).isEqualTo("ok");
        verify(rateLimiter, never()).setRate(RateType.OVERALL, 10, Duration.ofSeconds(60));
        verify(policyBucket, never()).set(anyString());
        verify(redissonClient, never()).getLock(anyString());
    }

    @Test
    @DisplayName("Should reset limiter when stored policy differs from current annotation")
    void checkRateLimit_mismatchedPolicy_shouldResetLimiter() throws Throwable {
        when(policyBucket.get()).thenReturn("20:60", "20:60");
        when(rateLimiter.tryAcquire()).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.checkRateLimit(joinPoint, rateLimit);

        assertThat(result).isEqualTo("ok");
        verify(redissonClient).getLock("rate_limit:GET:/api/v1/test:ip:127.0.0.1:anon:policy:lock");
        verify(policyLock).lock();
        verify(rateLimiter).setRate(RateType.OVERALL, 10, Duration.ofSeconds(60));
        verify(policyBucket).set("10:60");
        verify(policyLock).unlock();
    }

    @Test
    @DisplayName("Should handle missing request context")
    void checkRateLimit_noRequestContext_shouldProceed() throws Throwable {
        RequestContextHolder.resetRequestAttributes();

        when(rateLimiter.tryAcquire()).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.checkRateLimit(joinPoint, rateLimit);

        assertThat(result).isEqualTo("ok");
    }
}
