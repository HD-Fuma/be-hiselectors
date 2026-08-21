package com.fuma.hiselectors.content.dto;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.ContentType;
import java.time.LocalDateTime;
import java.util.List;

/** 관리자 콘텐츠 성과 목록 항목. */
public record ContentPerformanceResponse(
        Long contentId,
        Long selectorsId,
        String selectorsNickname,
        String generationName,
        SnsPlatform snsCode,
        String snsContentId,
        String contentUrl,
        ContentType contentType,
        LocalDateTime publishedAt,
        String accountId,
        Long followerCount,
        String profileImageUrl,
        List<String> texts,
        List<ContentInspectionMediaResponse> media,
        Long viewCount,
        Long likeCount,
        Long commentCount,
        List<TrendPoint> trend
) {
    public ContentPerformanceResponse {
        texts = List.copyOf(texts);
        media = List.copyOf(media);
        trend = List.copyOf(trend);
    }

    public record TrendPoint(
            LocalDateTime recordedAt,
            Long viewCount,
            Long likeCount,
            Long commentCount
    ) {
    }
}
