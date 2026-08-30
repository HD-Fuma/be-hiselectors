package com.fuma.hiselectors.inspection.extraction.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ContentMediaExtractionResultTest {

    @Test
    void acceptsTimestampAndNormalizedCoordinates() {
        ContentMediaExtractionResult result = new ContentMediaExtractionResult(
                "1.2",
                new ContentMediaExtractionResult.SttExtraction(
                        "ko", new ContentMediaExtractionResult.SttAudio(1_000L, 800L), List.of(
                        new ContentMediaExtractionResult.SttSegment(
                                "stt-001", 100L, 900L, "발화", -0.24, 0.03))),
                new ContentMediaExtractionResult.OcrExtraction(List.of(
                        new ContentMediaExtractionResult.OcrSegment(
                                "ocr-001", 200L, 800L, "화면 글자",
                                CoordinateSpace.NORMALIZED,
                                new NormalizedBoundingBox(0.1, 0.2, 0.3, 0.1)))));

        assertThat(result.stt().segments()).hasSize(1);
        assertThat(result.stt().audio().durationAfterVadMs()).isEqualTo(800L);
        assertThat(result.ocr().segments().getFirst().coordinateSpace())
                .isEqualTo(CoordinateSpace.NORMALIZED);
        assertThat(result.schemaVersion()).isEqualTo("1.2");
    }

    @Test
    void acceptsLegacyWorkerSchemaAndNormalizesToCurrent() {
        ContentMediaExtractionResult result = new ContentMediaExtractionResult(
                "1.1",
                ContentMediaExtractionResult.SttExtraction.empty(),
                ContentMediaExtractionResult.OcrExtraction.empty());

        assertThat(result.schemaVersion()).isEqualTo("1.2");
        assertThat(ContentMediaExtractionResult.supportsSchemaVersion("1.1")).isTrue();
    }

    @Test
    void rejectsOutOfScreenBoundingBox() {
        assertThatThrownBy(() -> new NormalizedBoundingBox(0.9, 0.1, 0.2, 0.2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDuplicateSegmentIdsAcrossModalities() {
        assertThatThrownBy(() -> new ContentMediaExtractionResult(
                "1.2",
                new ContentMediaExtractionResult.SttExtraction("ko", List.of(
                        new ContentMediaExtractionResult.SttSegment(
                                "same", 0L, 1L, "발화"))),
                new ContentMediaExtractionResult.OcrExtraction(List.of(
                        new ContentMediaExtractionResult.OcrSegment(
                                "same", 0L, 1L, "화면",
                                CoordinateSpace.NORMALIZED,
                                new NormalizedBoundingBox(0.1, 0.2, 0.3, 0.1))))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidSttQualityAndAudioMetadata() {
        assertThatThrownBy(() -> new ContentMediaExtractionResult.SttSegment(
                "stt-001", 0L, 1L, "발화", -0.2, 1.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ContentMediaExtractionResult.SttAudio(500L, 600L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
