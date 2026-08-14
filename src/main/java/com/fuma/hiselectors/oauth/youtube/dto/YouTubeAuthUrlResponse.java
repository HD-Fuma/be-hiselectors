package com.fuma.hiselectors.oauth.youtube.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "유튜브 채널 인증 시작 응답")
public record YouTubeAuthUrlResponse(
        @Schema(description = "프론트에서 이 URL로 리다이렉트하면 구글 로그인/동의 화면이 뜬다.",
                example = "https://accounts.google.com/o/oauth2/v2/auth?client_id=...")
        String authorizationUrl
) {

    public static YouTubeAuthUrlResponse of(String authorizationUrl) {
        return new YouTubeAuthUrlResponse(authorizationUrl);
    }
}
