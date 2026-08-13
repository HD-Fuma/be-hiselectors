package com.fuma.hiselectors.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;


@RestControllerAdvice
public class GlobalExceptionHandler {

    // 의도적인 비즈니스 예외
    // 사용법: service에서 throw new BusinessException(ErrorCode.SELECTOR_NOT_FOUND)
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(
            BusinessException e, HttpServletRequest request) {
        ErrorCode code = e.getErrorCode();
        // 예외에 담긴 메시지(기본 or 커스텀)를 그대로 사용
        ErrorResponse body = ErrorResponse.of(code, e.getMessage(), request.getRequestURI());
        return ResponseEntity.status(code.getStatus()).body(body);
    }

    // 잘못된 인자 -> 400
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException e, HttpServletRequest request) {
        ErrorCode code = ErrorCode.INVALID_INPUT;
        ErrorResponse body = ErrorResponse.of(code, e.getMessage(), request.getRequestURI());
        return ResponseEntity.status(code.getStatus()).body(body);
    }

    // @Valid 검증 실패 -> 400 (어떤 필드가 틀렸는지 메시지로)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = (fieldError != null)
                ? fieldError.getField() + ": " + fieldError.getDefaultMessage()
                : ErrorCode.INVALID_INPUT.getMessage();
        ErrorCode code = ErrorCode.INVALID_INPUT;
        ErrorResponse body = ErrorResponse.of(code, message, request.getRequestURI());
        return ResponseEntity.status(code.getStatus()).body(body);
    }

    // JSON 문법 오류 또는 Enum 등 요청 타입 변환 실패 -> 400
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(
            HttpMessageNotReadableException e, HttpServletRequest request) {
        ErrorCode code = ErrorCode.INVALID_INPUT;
        ErrorResponse body = ErrorResponse.of(code, request.getRequestURI());
        return ResponseEntity.status(code.getStatus()).body(body);
    }

    // 쿼리 파라미터 누락 또는 숫자/Enum 타입 변환 실패 -> 400
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleRequestParameter(
            Exception e, HttpServletRequest request) {
        ErrorCode code = ErrorCode.INVALID_INPUT;
        ErrorResponse body = ErrorResponse.of(code, request.getRequestURI());
        return ResponseEntity.status(code.getStatus()).body(body);
    }

    // 잘못된 HTTP 메서드 -> 405 (허용 메서드 안내 + Allow 헤더)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        ErrorCode code = ErrorCode.METHOD_NOT_ALLOWED;
        String supported = (e.getSupportedHttpMethods() != null)
                ? e.getSupportedHttpMethods().toString()
                : "없음";
        String message = "지원하지 않는 메서드입니다: " + e.getMethod() + " (허용: " + supported + ")";
        ErrorResponse body = ErrorResponse.of(code, message, request.getRequestURI());
        return ResponseEntity.status(code.getStatus())
                .allow(e.getSupportedHttpMethods() != null
                        ? e.getSupportedHttpMethods().toArray(new HttpMethod[0])
                        : new HttpMethod[0])
                .body(body);
    }

    // 존재하지 않는 경로 -> 404
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            NoResourceFoundException e, HttpServletRequest request) {
        ErrorCode code = ErrorCode.RESOURCE_NOT_FOUND;
        ErrorResponse body = ErrorResponse.of(code, request.getRequestURI());
        return ResponseEntity.status(code.getStatus()).body(body);
    }

    // 예상치 못한 에러 -> 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception e, HttpServletRequest request) {
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        ErrorResponse body = ErrorResponse.of(code, request.getRequestURI());
        return ResponseEntity.status(code.getStatus()).body(body);
    }
}
