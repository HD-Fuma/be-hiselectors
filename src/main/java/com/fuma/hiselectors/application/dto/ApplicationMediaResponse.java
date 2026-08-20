package com.fuma.hiselectors.application.dto;

import com.fuma.hiselectors.application.model.ApplicationMedia;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.ContentType;
import java.time.LocalDateTime;

public record ApplicationMediaResponse(
        Long id,
        Long applicationId,
        SnsPlatform snsCode,
        String snsContentId,
        String contentUrl,
        String mediaUrl,
        ContentType contentType,
        int sequenceNo,
        LocalDateTime publishedAt,
        Long viewCount,
        Long likeCount,
        Long commentCount,
        LocalDateTime collectedAt
) {
    public static ApplicationMediaResponse from(ApplicationMedia media) {
        return new ApplicationMediaResponse(
                media.getId(),
                media.getApplicationId(),
                media.getSnsCode(),
                media.getSnsContentId(),
                media.getContentUrl(),
                media.getMediaUrl(),
                media.getContentType(),
                media.getSequenceNo(),
                media.getPublishedAt(),
                media.getViewCount(),
                media.getLikeCount(),
                media.getCommentCount(),
                media.getCollectedAt());
    }
}
