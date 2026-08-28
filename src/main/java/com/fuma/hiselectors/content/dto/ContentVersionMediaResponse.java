package com.fuma.hiselectors.content.dto;

import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.MediaType;
import java.util.Map;

public record ContentVersionMediaResponse(
        Long contentMediaId,
        MediaType mediaType,
        String mediaUrl,
        String thumbnailUrl,
        String snsMediaId,
        Integer sequenceNo,
        Map<String, Object> body,
        String text
) {
    public ContentVersionMediaResponse {
        body = body == null ? Map.of() : Map.copyOf(body);
    }

    public static ContentVersionMediaResponse from(ContentMedia media) {
        Object bodyText = media.bodyOrEmpty().get("text");
        return new ContentVersionMediaResponse(
                media.getId(),
                media.getMediaType(),
                media.getMediaUrl(),
                media.getThumbnailUrl(),
                media.getSnsMediaId(),
                media.getSequenceNo(),
                media.bodyOrEmpty(),
                bodyText instanceof String value ? value : null);
    }
}
