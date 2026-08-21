package com.fuma.hiselectors.stt;

import io.swagger.v3.oas.annotations.media.Schema;

/** 지원자 콘텐츠 1건 분석·적재 요청. content_key 로 멱등(같은 키 재요청 시 재분석 안 함). */
public record ContentAddRequest(

        @Schema(description = "콘텐츠 고유 키 = Graph API media id(=sns_content_id). 멱등 키 겸 "
                + "media_url 만료 시 재취득 키(fetchMediaUrls). shortcode·permalink 아님.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String contentKey,

        @Schema(description = "Graph API media_url. 워커가 CDN 직다운")
        String mediaUrl,

        @Schema(description = "Graph API thumbnail_url. media_url 없을 때 폴백")
        String thumbnailUrl) {
}
