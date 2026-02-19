package com.vocawik.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vocawik.web.ClientIpResolver;
import com.vocawik.web.exception.TooManyRequestsException;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RedissonClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RateLimitAspectTest {

    private RedissonClient redissonClient;
    private RRateLimiter rateLimiter;
    private ClientIpResolver clientIpResolver;
    private RateLimitAspect aspect;
    private ProceedingJoinPoint joinPoint;
    private RateLimit rateLimit;

    @BeforeEach
    void setUp() {
        redissonClient = mock(RedissonClient.class);
        rateLimiter = mock(RRateLimiter.class);
        clientIpResolver = mock(ClientIpResolver.class);
        aspect = new RateLimitAspect(redissonClient, clientIpResolver);
        joinPoint = mock(ProceedingJoinPoint.class);
        rateLimit = mock(RateLimit.class);

        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimit.requests()).thenReturn(10);
        when(rateLimit.seconds()).thenReturn(60);

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
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("42", "N/A", List.of()));
        when(rateLimiter.tryAcquire()).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.checkRateLimit(joinPoint, rateLimit);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(redissonClient).getRateLimiter(captor.capture());
        assertThat(captor.getValue()).isEqualTo("rate_limit:GET:/api/v1/test:user:42:auth");
    }

    @Test
    @DisplayName("Should build user_or_ip key using resolved IP for anonymous user")
    void checkRateLimit_withAnonymousUser_shouldUseIpKey() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.50, 70.41.3.18");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(clientIpResolver.resolve(request)).thenReturn("203.0.113.50");

        when(rateLimiter.tryAcquire()).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.checkRateLimit(joinPoint, rateLimit);

        assertThat(result).isEqualTo("ok");
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(redissonClient).getRateLimiter(captor.capture());
        assertThat(captor.getValue()).isEqualTo("rate_limit:GET:/api/v1/test:ip:203.0.113.50:anon");
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
