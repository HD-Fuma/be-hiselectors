package com.fuma.hiselectors.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;


// 사용법: throw new BusinessException(HttpStatus.NOT_FOUND, "셀렉터를 찾을 수 없습니다.")
@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
