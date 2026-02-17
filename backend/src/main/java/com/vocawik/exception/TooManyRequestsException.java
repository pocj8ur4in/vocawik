package com.vocawik.exception;

/**
 * Thrown when a client exceeds the rate limit.
 *
 * @see com.vocawik.aop.RateLimit
 * @see com.vocawik.aop.RateLimitAspect
 */
public class TooManyRequestsException extends RuntimeException {

    /**
     * Creates a new exception with the given message.
     *
     * @param message the detail message
     */
    public TooManyRequestsException(String message) {
        super(message);
    }
}
