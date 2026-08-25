package com.fuma.hiselectors.content.dto;

import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.MediaType;

public record ContentVersionMediaResponse(
        Long contentMediaId,
        MediaType mediaType,
        String mediaUrl,
        String thumbnailUrl,
        String snsMediaId,
        Integer sequenceNo,
        String text
) {
    public static ContentVersionMediaResponse from(ContentMedia media) {
        Object bodyText = media.bodyOrEmpty().get("text");
        return new ContentVersionMediaResponse(
                media.getId(),
                media.getMediaType(),
                media.getMediaUrl(),
                media.getThumbnailUrl(),
                media.getSnsMediaId(),
                media.getSequenceNo(),
                bodyText instanceof String value ? value : null);
    }
}
