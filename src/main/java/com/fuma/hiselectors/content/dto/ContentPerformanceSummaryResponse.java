package com.fuma.hiselectors.content.dto;

import com.fuma.hiselectors.content.model.ContentType;
import java.util.List;

/** 관리자 콘텐츠 업로드·유형 요약. */
public record ContentPerformanceSummaryResponse(
        long totalContentCount,
        String currentGenerationName,
        long currentGenerationContentCount,
        String previousGenerationName,
        long previousGenerationContentCount,
        List<FormatCount> formats
) {
    public ContentPerformanceSummaryResponse {
        formats = List.copyOf(formats);
    }

    public record FormatCount(ContentType contentType, long count) {
    }
}
