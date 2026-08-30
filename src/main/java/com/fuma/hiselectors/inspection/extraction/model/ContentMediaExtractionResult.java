package com.fuma.hiselectors.inspection.extraction.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fuma.hiselectors.content.model.ContentReportAnalysis;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 콘텐츠 검수 전용 STT/OCR 근거 추출 계약이다. YouTube는 report를 함께 채운다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ContentMediaExtractionResult(
        String schemaVersion,
        SttExtraction stt,
        OcrExtraction ocr,
        ContentReportAnalysis report
) {

    public static final String CURRENT_SCHEMA_VERSION = "1.2";

    public ContentMediaExtractionResult {
        schemaVersion = normalizeSchemaVersion(schemaVersion);
        stt = stt == null ? SttExtraction.empty() : stt;
        ocr = ocr == null ? OcrExtraction.empty() : ocr;
        report = report == null ? ContentReportAnalysis.empty() : report;
        requireUniqueIds(stt.segments(), ocr.segments());
    }

    public ContentMediaExtractionResult(
            String schemaVersion, SttExtraction stt, OcrExtraction ocr) {
        this(schemaVersion, stt, ocr, ContentReportAnalysis.empty());
    }

    public static boolean supportsSchemaVersion(Object version) {
        return CURRENT_SCHEMA_VERSION.equals(version) || "1.1".equals(version);
    }

    private static String normalizeSchemaVersion(String schemaVersion) {
        if (schemaVersion == null || schemaVersion.isBlank()
                || CURRENT_SCHEMA_VERSION.equals(schemaVersion)
                || "1.1".equals(schemaVersion)) {
            return CURRENT_SCHEMA_VERSION;
        }
        throw new IllegalArgumentException("지원하지 않는 추출 스키마입니다: " + schemaVersion);
    }

    public static ContentMediaExtractionResult empty() {
        return new ContentMediaExtractionResult(
                CURRENT_SCHEMA_VERSION, SttExtraction.empty(), OcrExtraction.empty(),
                ContentReportAnalysis.empty());
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

    public sealed interface Segment permits SttSegment, OcrSegment {
        String segmentId();
    }

    public record SttExtraction(String language, SttAudio audio, List<SttSegment> segments) {
        public SttExtraction {
            language = language == null ? "" : language;
            audio = audio == null ? SttAudio.empty() : audio;
            segments = immutable(segments);
        }

        public SttExtraction(String language, List<SttSegment> segments) {
            this(language, SttAudio.empty(), segments);
        }

        public static SttExtraction empty() {
            return new SttExtraction("", SttAudio.empty(), List.of());
        }
    }

    public record SttAudio(Long durationMs, Long durationAfterVadMs) {
        public SttAudio {
            requireNonNegative(durationMs, "오디오 길이");
            requireNonNegative(durationAfterVadMs, "VAD 적용 오디오 길이");
            if (durationMs != null && durationAfterVadMs != null
                    && durationAfterVadMs > durationMs) {
                throw new IllegalArgumentException(
                        "VAD 적용 오디오 길이는 전체 오디오 길이를 초과할 수 없습니다.");
            }
        }

        public static SttAudio empty() {
            return new SttAudio(null, null);
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

    public record SttSegment(
            String segmentId,
            Long startMs,
            Long endMs,
            String text,
            Double avgLogProb,
            Double noSpeechProbability
    ) implements Segment {
        public SttSegment {
            requireIdAndText(segmentId, text);
            requireTime(startMs, endMs);
            requireFinite(avgLogProb, "STT 평균 로그 확률");
            requireProbability(noSpeechProbability, "STT 비음성 확률");
        }

        public SttSegment(String segmentId, Long startMs, Long endMs, String text) {
            this(segmentId, startMs, endMs, text, null, null);
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

    private static void requireNonNegative(Long value, String field) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(field + "는 0 이상이어야 합니다.");
        }
    }

    private static void requireFinite(Double value, String field) {
        if (value != null && !Double.isFinite(value)) {
            throw new IllegalArgumentException(field + "은 유한한 값이어야 합니다.");
        }
    }

    private static void requireProbability(Double value, String field) {
        requireFinite(value, field);
        if (value != null && (value < 0.0 || value > 1.0)) {
            throw new IllegalArgumentException(field + "은 0 이상 1 이하여야 합니다.");
        }
    }
}
