package com.fuma.hiselectors.content.dto;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.ContentType;
import java.time.LocalDateTime;

/** 관리자 콘텐츠 성과 목록을 조립하기 위한 콘텐츠·셀렉터스 조회 행. */
public record ContentPerformanceQueryRow(
        Long contentId,
        Long selectorsId,
        String selectorsNickname,
        SnsPlatform snsCode,
        String snsContentId,
        String contentUrl,
        ContentType contentType,
        LocalDateTime publishedAt,
        Long latestVersionId,
        String accountId,
        Long followerCount,
        String profileImageUrl
) {
}
