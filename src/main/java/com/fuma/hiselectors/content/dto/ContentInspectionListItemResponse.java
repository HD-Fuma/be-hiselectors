package com.fuma.hiselectors.content.dto;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.ContentType;
import java.time.LocalDateTime;
import java.util.List;

public record ContentInspectionListItemResponse(
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
        String inspectionStatus,
        LocalDateTime inspectedAt,
        LocalDateTime latestVersionStoredAt,
        String accountId,
        String profileImageUrl,
        String generationName,
        List<String> texts,
        List<ContentInspectionMediaResponse> media
) {

    public ContentInspectionListItemResponse {
        texts = List.copyOf(texts);
        media = List.copyOf(media);
    }
}
