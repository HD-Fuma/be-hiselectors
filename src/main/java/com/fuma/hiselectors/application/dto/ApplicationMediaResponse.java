package com.fuma.hiselectors.application.dto;

import com.fuma.hiselectors.application.model.ApplicationMedia;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.content.model.MediaType;
import java.time.LocalDateTime;

public record ApplicationMediaResponse(
        Long id,
        Long applicationId,
        SnsPlatform snsCode,
        String snsContentId,
        String snsMediaId,
        String contentUrl,
        String mediaUrl,
        String thumbnailUrl,
        ContentType contentType,
        MediaType mediaType,
        String caption,
        String title,
        String description,
        int sequenceNo,
        int mediaSequenceNo,
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
                media.getSnsMediaId(),
                media.getContentUrl(),
                media.getMediaUrl(),
                media.getThumbnailUrl(),
                normalize(media.getContentType()),
                media.getMediaType(),
                media.getCaption(),
                media.getTitle(),
                media.getDescription(),
                media.getSequenceNo(),
                media.getMediaSequenceNo(),
                media.getPublishedAt(),
                media.getViewCount(),
                media.getLikeCount(),
                media.getCommentCount(),
                media.getCollectedAt());
    }

    private static ContentType normalize(ContentType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case SHORT_FORM -> ContentType.REELS;
            case FEED -> ContentType.POST;
            default -> type;
        };
    }
}
