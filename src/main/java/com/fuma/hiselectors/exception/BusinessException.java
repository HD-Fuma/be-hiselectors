package com.fuma.hiselectors.exception;

import lombok.Getter;

/* 비즈니스 로직에서 의도적으로 던지는 예외.
 * 사용법: {throw new BusinessException(ErrorCode.SELECTOR_NOT_FOUND)}
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    // 메시지를 본인이 적고 싶을 경우, message 부분에 넣어서 전달
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
