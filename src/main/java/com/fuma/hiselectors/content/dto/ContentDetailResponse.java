package com.fuma.hiselectors.content.dto;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.ContentType;
import java.time.LocalDateTime;
import java.util.List;

public record ContentDetailResponse(
        Long contentId,
        Long selectorsId,
        SnsPlatform snsCode,
        String snsContentId,
        String contentUrl,
        ContentType contentType,
        LocalDateTime storedAt,
        List<ContentVersionSummaryResponse> versions,
        ContentVersionDetailResponse selectedVersion
) {
    public ContentDetailResponse {
        versions = List.copyOf(versions);
    }
}
