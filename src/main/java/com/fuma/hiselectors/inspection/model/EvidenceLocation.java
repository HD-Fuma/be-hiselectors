package com.fuma.hiselectors.inspection.model;

import com.fuma.hiselectors.content.model.MediaType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 저장된 텍스트 범위 또는 추출 segment를 가리키는 위반 근거 참조다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvidenceLocation(
        Long contentMediaId,
        MediaType mediaType,
        EvidenceTargetKind targetKind,
        EvidenceCoordinateSpace coordinateSpace,
        String segmentId,
        Integer startIndex,
        Integer endIndex,
        String excerpt
) {
}
