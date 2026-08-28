package com.fuma.hiselectors.inspection.extraction.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 콘텐츠 검수 전용 STT/OCR/시각 근거 추출 계약이다. */
public record ContentMediaExtractionResult(
        String schemaVersion,
        SttExtraction stt,
        OcrExtraction ocr,
        VisualExtraction visual
) {

    public static final String CURRENT_SCHEMA_VERSION = "1.0";

    public ContentMediaExtractionResult {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank()
                ? CURRENT_SCHEMA_VERSION : schemaVersion;
        stt = stt == null ? SttExtraction.empty() : stt;
        ocr = ocr == null ? OcrExtraction.empty() : ocr;
        visual = visual == null ? VisualExtraction.empty() : visual;
        if (!CURRENT_SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("지원하지 않는 추출 스키마입니다: " + schemaVersion);
        }
        requireUniqueIds(stt.segments(), ocr.segments(), visual.segments());
    }

    public static ContentMediaExtractionResult empty() {
        return new ContentMediaExtractionResult(
                CURRENT_SCHEMA_VERSION, SttExtraction.empty(),
                OcrExtraction.empty(), VisualExtraction.empty());
    }

    @SafeVarargs
    private static void requireUniqueIds(List<? extends Segment>... groups) {
        Set<String> ids = new HashSet<>();
        for (List<? extends Segment> group : groups) {
            for (Segment segment : group) {
                if (!ids.add(segment.segmentId())) {
                    throw new IllegalArgumentException(
                            "중복된 extraction segmentId입니다: " + segment.segmentId());
                }
            }
        }
    }

    public sealed interface Segment permits SttSegment, OcrSegment, VisualSegment {
        String segmentId();
    }

    public record SttExtraction(String language, List<SttSegment> segments) {
        public SttExtraction {
            language = language == null ? "" : language;
            segments = immutable(segments);
        }

        public static SttExtraction empty() {
            return new SttExtraction("", List.of());
        }
    }

    public record OcrExtraction(List<OcrSegment> segments) {
        public OcrExtraction {
            segments = immutable(segments);
        }

        public static OcrExtraction empty() {
            return new OcrExtraction(List.of());
        }
    }

    public record VisualExtraction(List<VisualSegment> segments) {
        public VisualExtraction {
            segments = immutable(segments);
        }

        public static VisualExtraction empty() {
            return new VisualExtraction(List.of());
        }
    }

    public record SttSegment(
            String segmentId,
            Long startMs,
            Long endMs,
            String text
    ) implements Segment {
        public SttSegment {
            requireIdAndText(segmentId, text);
            requireTime(startMs, endMs);
        }
    }

    public record OcrSegment(
            String segmentId,
            Long startMs,
            Long endMs,
            String text,
            CoordinateSpace coordinateSpace,
            NormalizedBoundingBox bbox
    ) implements Segment {
        public OcrSegment {
            requireIdAndText(segmentId, text);
            requireOptionalTime(startMs, endMs);
            if (coordinateSpace != CoordinateSpace.NORMALIZED || bbox == null) {
                throw new IllegalArgumentException(
                        "OCR bbox는 NORMALIZED 좌표로 제공해야 합니다.");
            }
        }
    }

    public record VisualSegment(
            String segmentId,
            Long startMs,
            Long endMs,
            String description
    ) implements Segment {
        public VisualSegment {
            requireIdAndText(segmentId, description);
            requireTime(startMs, endMs);
        }
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static void requireIdAndText(String id, String text) {
        if (id == null || id.isBlank() || text == null || text.isBlank()) {
            throw new IllegalArgumentException("segmentId와 근거 텍스트는 필수입니다.");
        }
    }

    private static void requireTime(Long startMs, Long endMs) {
        if (startMs == null || endMs == null || startMs < 0 || endMs <= startMs) {
            throw new IllegalArgumentException("시간 좌표는 0 이상의 [startMs, endMs)여야 합니다.");
        }
    }

    private static void requireOptionalTime(Long startMs, Long endMs) {
        if ((startMs == null) != (endMs == null)) {
            throw new IllegalArgumentException("시간 좌표 시작과 끝은 함께 제공해야 합니다.");
        }
        if (startMs != null) {
            requireTime(startMs, endMs);
        }
    }
}
