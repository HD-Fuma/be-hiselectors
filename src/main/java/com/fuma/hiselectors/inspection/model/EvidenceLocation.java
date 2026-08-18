package com.fuma.hiselectors.inspection.model;

import com.fuma.hiselectors.content.model.MediaType;

public record EvidenceLocation(
        Long contentMediaId,
        MediaType mediaType,
        Integer startIndex,
        Integer endIndex,
        Double startTime,
        Double endTime,
        BoundingBox bbox,
        String excerpt
) {
}
