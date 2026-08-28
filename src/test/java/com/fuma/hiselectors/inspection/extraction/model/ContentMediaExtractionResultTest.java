package com.fuma.hiselectors.inspection.extraction.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ContentMediaExtractionResultTest {

    @Test
    void acceptsTimestampAndNormalizedCoordinates() {
        ContentMediaExtractionResult result = new ContentMediaExtractionResult(
                "1.0",
                new ContentMediaExtractionResult.SttExtraction("ko", List.of(
                        new ContentMediaExtractionResult.SttSegment(
                                "stt-001", 100L, 900L, "발화"))),
                new ContentMediaExtractionResult.OcrExtraction(List.of(
                        new ContentMediaExtractionResult.OcrSegment(
                                "ocr-001", 200L, 800L, "화면 글자",
                                CoordinateSpace.NORMALIZED,
                                new NormalizedBoundingBox(0.1, 0.2, 0.3, 0.1)))),
                new ContentMediaExtractionResult.VisualExtraction(List.of(
                        new ContentMediaExtractionResult.VisualSegment(
                                "visual-001", 0L, 1_000L, "사람이 제품을 들어 보인다."))));

        assertThat(result.stt().segments()).hasSize(1);
        assertThat(result.ocr().segments().getFirst().coordinateSpace())
                .isEqualTo(CoordinateSpace.NORMALIZED);
        assertThat(result.visual().segments()).hasSize(1);
    }

    @Test
    void rejectsOutOfScreenBoundingBox() {
        assertThatThrownBy(() -> new NormalizedBoundingBox(0.9, 0.1, 0.2, 0.2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDuplicateSegmentIdsAcrossModalities() {
        assertThatThrownBy(() -> new ContentMediaExtractionResult(
                "1.0",
                new ContentMediaExtractionResult.SttExtraction("ko", List.of(
                        new ContentMediaExtractionResult.SttSegment(
                                "same", 0L, 1L, "발화"))),
                ContentMediaExtractionResult.OcrExtraction.empty(),
                new ContentMediaExtractionResult.VisualExtraction(List.of(
                        new ContentMediaExtractionResult.VisualSegment(
                                "same", 0L, 1L, "장면")))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
