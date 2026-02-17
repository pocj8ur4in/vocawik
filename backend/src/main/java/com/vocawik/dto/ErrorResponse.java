package com.vocawik.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import org.springframework.http.HttpStatus;

/** Standard error response body. */
@Builder
public record ErrorResponse(int code, String message, String status, LocalDateTime timestamp) {

    /** Creates an error response using the HTTP status name as the status code. */
    public static ErrorResponse of(HttpStatus httpStatus, String message) {
        return of(httpStatus, message, httpStatus.name());
    }

    /** Creates an error response with an explicit machine-readable status code. */
    public static ErrorResponse of(HttpStatus httpStatus, String message, String status) {
        return ErrorResponse.builder()
                .code(httpStatus.value())
                .message(message)
                .status(status)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
