package com.fuma.hiselectors.oauth.instagram.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "인스타그램 계정 인증 시작 응답")
public record InstagramAuthUrlResponse(
        @Schema(description = "프론트에서 이 URL로 리다이렉트하면 인스타그램 로그인/동의 화면이 뜬다.",
                example = "https://www.instagram.com/oauth/authorize?client_id=...")
        String authorizationUrl
) {

    public static InstagramAuthUrlResponse of(String authorizationUrl) {
        return new InstagramAuthUrlResponse(authorizationUrl);
    }
}
