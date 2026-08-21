package com.fuma.hiselectors.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 응답 (JWT 토큰)")
public record TokenResponse(
        @Schema(description = "발급된 JWT 액세스 토큰", example = "eyJhbGciOiJIUzUxMiJ9...")
        String accessToken,
        @Schema(description = "토큰 타입", example = "Bearer")
        String tokenType,
        @Schema(description = "로그인 아이디", example = "hiadmin")
        String loginId,
        @Schema(description = "권한 (USER / ADMIN)", example = "ADMIN")
        String role,
        @Schema(description = "알림톡 수신 동의 여부 (Y/N), 어드민은 null", example = "Y")
        String alimtalk,
        @Schema(description = "로그인 사용자 이름", example = "홍길동")
        String name
) {

    public static TokenResponse of(String accessToken, String loginId, String role) {
        return new TokenResponse(accessToken, "Bearer", loginId, role, null, null);
    }

    public static TokenResponse of(String accessToken, String loginId, String role, String alimtalk) {
        return new TokenResponse(accessToken, "Bearer", loginId, role, alimtalk, null);
    }

    public static TokenResponse ofAdmin(String accessToken, String loginId, String role, String name) {
        return new TokenResponse(accessToken, "Bearer", loginId, role, null, name);
    }
}
