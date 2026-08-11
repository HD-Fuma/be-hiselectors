package com.fuma.hiselectors.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;


@RestControllerAdvice
public class GlobalExceptionHandler {

    // 의도적인 비즈니스 로직 예외
    // 사용법: service에서 throw new BusinessException(HttpStatus.NOT_FOUND, "셀렉터를 찾을 수 없습니다.")
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(
            BusinessException e, HttpServletRequest request) {
        HttpStatus status = e.getStatus();
        ErrorResponse body = ErrorResponse.of(
                status.value(), status.getReasonPhrase(), e.getMessage(), request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }


    // 잘못된 인자 -> 400
    // 사용법: throw new IllegalArgumentException("id는 0보다 커야 합니다.")
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ErrorResponse body = ErrorResponse.of(
                status.value(), status.getReasonPhrase(), e.getMessage(), request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    // @Valid 검증 실패 -> 400 -> DTO 검증 실패 시 어떤 필드가 틀렸는지
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = (fieldError != null)
                ? fieldError.getField() + ": " + fieldError.getDefaultMessage()
                : "요청 값이 올바르지 않습니다.";
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ErrorResponse body = ErrorResponse.of(
                status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }


    // 존재하지 않는 경로(매칭되는 핸들러 없음) -> 404
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            NoResourceFoundException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        ErrorResponse body = ErrorResponse.of(
                status.value(), status.getReasonPhrase(), "요청한 리소스를 찾을 수 없습니다.", request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    // 예상치 못한 에러 -> 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        ErrorResponse body = ErrorResponse.of(
                status.value(), status.getReasonPhrase(), "서버 오류가 발생했습니다.", request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
