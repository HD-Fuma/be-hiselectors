package com.fuma.hiselectors.exception;

import com.fuma.hiselectors.common.ApiResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;


@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static ResponseEntity<ApiResult<Void>> build(ErrorCode code, String message) {
        return ResponseEntity.status(code.getStatus())
                .body(ApiResult.error(code.name(), message));
    }

    // 의도적인 비즈니스 예외
    // 사용법: service에서 throw new BusinessException(ErrorCode.SELECTOR_NOT_FOUND)
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> handleBusiness(BusinessException e) {
        // 예외에 담긴 메시지(기본 or 커스텀)를 그대로 사용
        return build(e.getErrorCode(), e.getMessage());
    }

    // 잘못된 인자 -> 400
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResult<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return build(ErrorCode.INVALID_INPUT, e.getMessage());
    }

    // @Valid 검증 실패 -> 400 (어떤 필드가 틀렸는지 메시지로)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = (fieldError != null)
                ? fieldError.getField() + ": " + fieldError.getDefaultMessage()
                : ErrorCode.INVALID_INPUT.getMessage();
        return build(ErrorCode.INVALID_INPUT, message);
    }

    // @Validated 메서드 파라미터 검증 실패 -> 400
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResult<Void>> handleConstraintViolation(ConstraintViolationException e) {
        ConstraintViolation<?> violation = e.getConstraintViolations().stream()
                .findFirst()
                .orElse(null);
        String message = violation != null
                ? violation.getPropertyPath() + ": " + violation.getMessage()
                : ErrorCode.INVALID_INPUT.getMessage();
        return build(ErrorCode.INVALID_INPUT, message);
    }

    // JSON 문법 오류 또는 Enum 등 요청 타입 변환 실패 -> 400
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResult<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return build(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getMessage());
    }

    // 요청 파라미터/헤더 누락 또는 숫자/Enum/UUID 타입 변환 실패 -> 400
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class,
            MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResult<Void>> handleRequestParameter(Exception e) {
        return build(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResult<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return build(ErrorCode.INVALID_INPUT, "업로드 파일은 5MB 이하여야 합니다.");
    }

    // 잘못된 HTTP 메서드 -> 405 (허용 메서드 안내 + Allow 헤더)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResult<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e) {
        ErrorCode code = ErrorCode.METHOD_NOT_ALLOWED;
        String supported = (e.getSupportedHttpMethods() != null)
                ? e.getSupportedHttpMethods().toString()
                : "없음";
        String message = "지원하지 않는 메서드입니다: " + e.getMethod() + " (허용: " + supported + ")";
        return ResponseEntity.status(code.getStatus())
                .allow(e.getSupportedHttpMethods() != null
                        ? e.getSupportedHttpMethods().toArray(new HttpMethod[0])
                        : new HttpMethod[0])
                .body(ApiResult.error(code.name(), message));
    }

    // 존재하지 않는 경로 -> 404
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResult<Void>> handleNotFound(NoResourceFoundException e) {
        return build(ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getMessage());
    }

    // 예상치 못한 에러 -> 500 (원인은 로그로 남긴다)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage());
    }
}
