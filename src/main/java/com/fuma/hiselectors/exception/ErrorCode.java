package com.fuma.hiselectors.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // --- 공통 ---
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
    // --- 인증 / 인가 ---
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // --- 유튜브 OAuth ---
    YOUTUBE_STATE_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 인증 요청입니다."),
    YOUTUBE_OAUTH_FAILED(HttpStatus.BAD_GATEWAY, "유튜브 인증 처리 중 오류가 발생했습니다."),
    YOUTUBE_CHANNEL_NOT_FOUND(HttpStatus.NOT_FOUND, "연결된 유튜브 채널을 찾을 수 없습니다."),

    // --- 인스타그램 OAuth ---
    INSTAGRAM_STATE_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 인증 요청입니다."),
    INSTAGRAM_OAUTH_FAILED(HttpStatus.BAD_GATEWAY, "인스타그램 인증 처리 중 오류가 발생했습니다."),
    INSTAGRAM_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "연결된 인스타그램 계정을 찾을 수 없습니다."),

    // --- 도메인 (예시) ---
    SELECTOR_NOT_FOUND(HttpStatus.NOT_FOUND, "셀렉터스를 찾을 수 없습니다."),

    // --- 구매 ---
    PURCHASE_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "구매자를 찾을 수 없습니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    PRODUCT_NOT_AVAILABLE(HttpStatus.CONFLICT, "판매할 수 없는 상품입니다."),
    PURCHASE_NOT_FOUND(HttpStatus.NOT_FOUND, "구매 이력을 찾을 수 없습니다."),
    INVALID_PURCHASE_AMOUNT(HttpStatus.BAD_REQUEST, "구매 금액이 올바르지 않습니다."),
    PURCHASE_CONFLICT(HttpStatus.CONFLICT, "기존 구매 정보와 요청 정보가 일치하지 않습니다."),
    INVALID_PURCHASE_STATUS_TRANSITION(HttpStatus.CONFLICT, "허용되지 않는 구매 상태 변경입니다."),

    // --- 발굴 카테고리 / 키워드 ---
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다."),
    CATEGORY_CODE_DUPLICATED(HttpStatus.CONFLICT, "이미 존재하는 카테고리 코드입니다."),
    CATEGORY_NAME_DUPLICATED(HttpStatus.CONFLICT, "이미 존재하는 카테고리 이름입니다."),
    KEYWORD_NOT_FOUND(HttpStatus.NOT_FOUND, "발굴 키워드를 찾을 수 없습니다."),
    KEYWORD_DUPLICATED(HttpStatus.CONFLICT, "이미 등록된 발굴 키워드입니다."),
    CATEGORY_IN_USE(HttpStatus.CONFLICT, "발굴 이력이 있어 삭제할 수 없습니다. 비활성화를 사용하세요."),
    KEYWORD_IN_USE(HttpStatus.CONFLICT, "발굴 이력이 있어 삭제할 수 없습니다. 비활성화를 사용하세요."),

    // --- 크리에이터 ---
    CREATOR_NOT_FOUND(HttpStatus.NOT_FOUND, "크리에이터를 찾을 수 없습니다."),

    // --- 지원 ---
    APPLICATION_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "지원자를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
