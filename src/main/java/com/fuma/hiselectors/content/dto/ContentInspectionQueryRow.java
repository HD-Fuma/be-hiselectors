package com.fuma.hiselectors.content.dto;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.content.model.ContentVersionStatus;
import java.time.LocalDateTime;

/** 관리자 콘텐츠 검수 목록을 조립하기 위한 최신 버전 조회 행. */
public record ContentInspectionQueryRow(
        Long contentId,
        Long selectorsId,
        String selectorsNickname,
        SnsPlatform snsCode,
        String snsContentId,
        String contentUrl,
        ContentType contentType,
        LocalDateTime storedAt,
        Long latestVersionId,
        Long latestVersionNo,
        ContentVersionStatus inspectionStatus,
        LocalDateTime inspectedAt,
        LocalDateTime latestVersionStoredAt,
        String accountId,
        String profileImageUrl
) {
}
