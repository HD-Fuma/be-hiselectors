package com.fuma.hiselectors.inspection.dto;

import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.inspection.extraction.model.CoordinateSpace;
import com.fuma.hiselectors.inspection.extraction.model.NormalizedBoundingBox;
import com.fuma.hiselectors.inspection.model.EvidenceCoordinateSpace;
import com.fuma.hiselectors.inspection.model.EvidenceLocation;
import com.fuma.hiselectors.inspection.model.EvidenceTargetKind;
import java.util.List;
import java.util.Map;

/** 저장된 segment 참조를 클라이언트가 바로 사용할 수 있는 좌표로 해석한 조회 DTO다. */
public record EvidenceLocationResponse(
        Long contentMediaId,
        MediaType mediaType,
        EvidenceTargetKind targetKind,
        EvidenceCoordinateSpace coordinateSpace,
        String segmentId,
        Integer startIndex,
        Integer endIndex,
        Long startMs,
        Long endMs,
        CoordinateSpace bboxCoordinateSpace,
        NormalizedBoundingBox bbox,
        String excerpt
) {

    public static EvidenceLocationResponse from(
            EvidenceLocation location, ContentMedia media) {
        SegmentCoordinates coordinates = coordinates(location, media);
        return new EvidenceLocationResponse(
                location.contentMediaId(), location.mediaType(), location.targetKind(),
                location.coordinateSpace(), location.segmentId(),
                location.startIndex(), location.endIndex(),
                coordinates.startMs(), coordinates.endMs(),
                coordinates.bboxCoordinateSpace(), coordinates.bbox(), location.excerpt());
    }

    private static SegmentCoordinates coordinates(
            EvidenceLocation location, ContentMedia media) {
        if (media == null || location.targetKind() == null || location.segmentId() == null) {
            return SegmentCoordinates.empty();
        }
        String groupName = switch (location.targetKind()) {
            case STT_SEGMENT -> "stt";
            case OCR_SEGMENT -> "ocr";
            case VISUAL_SEGMENT -> "visual";
            default -> null;
        };
        if (groupName == null) {
            return SegmentCoordinates.empty();
        }
        Object groupValue = media.bodyOrEmpty().get(groupName);
        if (!(groupValue instanceof Map<?, ?> group)
                || !(group.get("segments") instanceof List<?> segments)) {
            return SegmentCoordinates.empty();
        }
        return segments.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(segment -> location.segmentId().equals(segment.get("segmentId")))
                .map(EvidenceLocationResponse::coordinates)
                .findFirst()
                .orElseGet(SegmentCoordinates::empty);
    }

    private static SegmentCoordinates coordinates(Map<?, ?> segment) {
        Long startMs = longValue(segment.get("startMs"));
        Long endMs = longValue(segment.get("endMs"));
        if (!(segment.get("bbox") instanceof Map<?, ?> bboxValue)) {
            return new SegmentCoordinates(startMs, endMs, null, null);
        }
        Double x = doubleValue(bboxValue.get("x"));
        Double y = doubleValue(bboxValue.get("y"));
        Double width = doubleValue(bboxValue.get("width"));
        Double height = doubleValue(bboxValue.get("height"));
        if (x == null || y == null || width == null || height == null) {
            return new SegmentCoordinates(startMs, endMs, null, null);
        }
        NormalizedBoundingBox bbox = new NormalizedBoundingBox(x, y, width, height);
        CoordinateSpace coordinateSpace = "NORMALIZED".equals(segment.get("coordinateSpace"))
                ? CoordinateSpace.NORMALIZED : null;
        return new SegmentCoordinates(startMs, endMs, coordinateSpace, bbox);
    }

    private static Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static Double doubleValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private record SegmentCoordinates(
            Long startMs,
            Long endMs,
            CoordinateSpace bboxCoordinateSpace,
            NormalizedBoundingBox bbox) {

        private static SegmentCoordinates empty() {
            return new SegmentCoordinates(null, null, null, null);
        }
    }
}
