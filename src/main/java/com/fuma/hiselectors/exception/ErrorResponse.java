package com.fuma.hiselectors.exception;

import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;

public record ErrorResponse(
        LocalDateTime timestamp,
        String code,
        int status,
        String error,
        String message,
        String path
) {

    public static ErrorResponse of(ErrorCode errorCode, String path) {
        return of(errorCode, errorCode.getMessage(), path);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, String path) {
        HttpStatus status = errorCode.getStatus();
        return new ErrorResponse(
                LocalDateTime.now(),
                errorCode.name(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path);
    }
}
