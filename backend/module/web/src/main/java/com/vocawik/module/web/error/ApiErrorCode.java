package com.vocawik.module.web.error;

import org.springframework.http.HttpStatus;

/** Contract for API error codes that can be rendered as HTTP error responses. */
public interface ApiErrorCode {

    /**
     * Returns the HTTP status associated with the error code.
     *
     * @return HTTP status
     */
    HttpStatus httpStatus();

    /**
     * Returns the stable machine-readable error status.
     *
     * @return machine-readable error status
     */
    String status();

    /**
     * Returns the default human-readable error message.
     *
     * @return default error message
     */
    String message();
}
