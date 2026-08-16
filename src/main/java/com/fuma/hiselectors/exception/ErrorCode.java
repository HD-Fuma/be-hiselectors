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

    // --- 카카오 OAuth / 메시지 ---
    KAKAO_CONFIGURATION_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "카카오 연동 설정이 올바르지 않습니다."),
    KAKAO_STATE_INVALID(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 카카오 인증 요청입니다."),
    KAKAO_OAUTH_FAILED(HttpStatus.BAD_GATEWAY, "카카오 인증 처리 중 오류가 발생했습니다."),
    KAKAO_REQUIRED_SCOPE_MISSING(HttpStatus.FORBIDDEN, "카카오 필수 동의항목에 동의가 필요합니다."),
    KAKAO_SENDER_NOT_CONNECTED(HttpStatus.CONFLICT, "연결된 카카오 발신 계정이 없습니다."),
    KAKAO_SENDER_REAUTH_REQUIRED(HttpStatus.CONFLICT, "카카오 발신 계정 재인증이 필요합니다."),
    KAKAO_TOKEN_REFRESH_FAILED(HttpStatus.BAD_GATEWAY, "카카오 토큰을 갱신할 수 없습니다."),
    KAKAO_TOKEN_DECRYPTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "카카오 토큰을 복호화할 수 없습니다."),
    KAKAO_RECIPIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "카카오 수신 연결 정보를 찾을 수 없습니다."),
    KAKAO_RECIPIENT_NOT_READY(HttpStatus.CONFLICT, "카카오 메시지를 수신할 수 없는 상태입니다."),
    KAKAO_RECIPIENT_INVALID(HttpStatus.CONFLICT, "카카오 수신자 정보가 유효하지 않아 재연결이 필요합니다."),
    KAKAO_FRIEND_NOT_FOUND(HttpStatus.CONFLICT, "발신 계정의 친구 목록에서 사용자를 찾을 수 없습니다. 최대 10분 후 다시 시도해주세요."),
    KAKAO_CONNECTION_DUPLICATED(HttpStatus.CONFLICT, "이미 다른 사용자와 연결된 카카오 계정 또는 UUID입니다."),
    KAKAO_API_CALL_FAILED(HttpStatus.BAD_GATEWAY, "카카오 API 호출에 실패했습니다."),
    KAKAO_MESSAGE_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "카카오 메시지 발송 한도를 초과했습니다."),
    KAKAO_MESSAGE_SEND_FAILED(HttpStatus.BAD_GATEWAY, "카카오톡 메시지 발송에 실패했습니다."),

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

    // --- 발굴 실행 ---
    YOUTUBE_API_KEY_MISSING(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "YouTube API 키가 설정되지 않았습니다."
    ),
    YOUTUBE_API_CALL_FAILED(
            HttpStatus.BAD_GATEWAY,
            "YouTube API 호출에 실패했습니다. 쿼터 초과 여부를 확인하세요."
    ),
    META_GRAPH_CONFIG_MISSING(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Meta Graph API 설정이 누락되었습니다."
    ),
    META_GRAPH_API_CALL_FAILED(
            HttpStatus.BAD_GATEWAY,
            "Meta Graph API 호출에 실패했습니다. 토큰과 권한을 확인하세요."
    ),
    INSTAGRAM_DISCOVERY_ACCOUNT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "조회 가능한 Instagram 비즈니스 계정을 찾을 수 없습니다."
    ),
    INSTAGRAM_HANDLE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "YouTube 채널에서 추출된 Instagram 사용자명이 없습니다."
    ),

    // --- 지원 ---

    APPLICATION_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "지원자를 찾을 수 없습니다."),
    GENERATION_NOT_FOUND(HttpStatus.NOT_FOUND, "기수를 찾을 수 없습니다."),
    GENERATION_PERIOD_INVALID(HttpStatus.BAD_REQUEST, "모집 시작일은 종료일보다 빨라야 합니다."),
    ACTIVE_GENERATION_OVERLAPPED(HttpStatus.CONFLICT, "모집 기간이 겹치는 활성 기수가 있습니다."),
    ACTIVE_GENERATION_NOT_FOUND(HttpStatus.CONFLICT, "현재 모집 중인 기수가 없어 지원할 수 없습니다."),
    DUPLICATE_APPLICATION(HttpStatus.CONFLICT, "이미 해당 기수에 지원했습니다."),

    // --- STT (Gemini) ---
    GEMINI_API_KEY_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "Gemini API 키가 설정되지 않았습니다."),
    GEMINI_API_CALL_FAILED(HttpStatus.BAD_GATEWAY, "Gemini API 호출에 실패했습니다."),
    STT_SNS_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "STT를 지원하지 않는 SNS 플랫폼입니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
