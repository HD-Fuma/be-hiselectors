package com.fuma.hiselectors.stt;

import io.swagger.v3.oas.annotations.media.Schema;

/** 지원자 콘텐츠 1건 분석·적재 요청. content_key 로 멱등(같은 키 재요청 시 재분석 안 함). */
public record ContentAddRequest(

        @Schema(description = "콘텐츠 고유 키(릴스 shortcode 또는 media id). 멱등 키", requiredMode =
                Schema.RequiredMode.REQUIRED)
        String contentKey,

        @Schema(description = "릴스 permalink(yt-dlp 폴백용)")
        String reelUrl,

        @Schema(description = "Graph API media_url. 있으면 CDN 직다운(yt-dlp 생략)")
        String mediaUrl,

        @Schema(description = "Graph API thumbnail_url. 영상 실패 시 폴백")
        String thumbnailUrl) {
}
