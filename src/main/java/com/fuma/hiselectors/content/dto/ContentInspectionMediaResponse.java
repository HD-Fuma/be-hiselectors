package com.fuma.hiselectors.content.dto;

import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.MediaType;

public record ContentInspectionMediaResponse(
        MediaType mediaType,
        String mediaUrl,
        String snsMediaId,
        Integer sequenceNo
) {

    public static ContentInspectionMediaResponse from(ContentMedia media) {
        return new ContentInspectionMediaResponse(
                media.getMediaType(),
                media.getMediaUrl(),
                media.getSnsMediaId(),
                media.getSequenceNo());
    }
}
