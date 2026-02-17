package com.vocawik.exception;

/**
 * Thrown when an unauthenticated request accesses a protected resource.
 *
 * @see com.vocawik.security.CurrentUserArgumentResolver
 */
public class UnauthorizedException extends RuntimeException {

    /**
     * Creates a new exception with the given message.
     *
     * @param message the detail message
     */
    public UnauthorizedException(String message) {
        super(message);
    }
}
