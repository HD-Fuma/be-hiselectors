package com.fuma.hiselectors.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.content.model.ContentReportAnalysis;
import com.fuma.hiselectors.inspection.extraction.model.ContentMediaExtractionResult;
import com.fuma.hiselectors.inspection.extraction.model.CoordinateSpace;
import com.fuma.hiselectors.inspection.extraction.model.NormalizedBoundingBox;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ContentMediaExtractionBodyMapperTest {

    private final ContentMediaExtractionBodyMapper mapper =
            new ContentMediaExtractionBodyMapper(new ObjectMapper());

    @Test
    void roundTripsStructuredExtractionBody() {
        ContentMediaExtractionResult extraction = new ContentMediaExtractionResult(
                "1.2",
                new ContentMediaExtractionResult.SttExtraction("ko", List.of(
                        new ContentMediaExtractionResult.SttSegment(
                                "stt-001", 0L, 500L, "speech"))),
                new ContentMediaExtractionResult.OcrExtraction(List.of(
                        new ContentMediaExtractionResult.OcrSegment(
                                "ocr-001", null, null, "screen text",
                                CoordinateSpace.NORMALIZED,
                                new NormalizedBoundingBox(0.1, 0.2, 0.3, 0.4)))));

        Map<String, Object> body = mapper.toBody(extraction);

        assertThat(body).containsKeys("schemaVersion", "stt", "ocr")
                .doesNotContainKey("visual");
        assertThat(mapper.isCurrentExtraction(body)).isTrue();
        assertThat(mapper.fromBody(body)).isEqualTo(extraction);
    }

    @Test
    void acceptsLegacyWorkerSchemaAsCurrentExtraction() {
        Map<String, Object> body = Map.of(
                "schemaVersion", "1.1",
                "stt", Map.of("language", "ko", "segments", List.of()),
                "ocr", Map.of("segments", List.of()),
                "visual", Map.of("segments", List.of()));

        assertThat(mapper.isCurrentExtraction(body)).isTrue();
        assertThat(mapper.fromBody(body).schemaVersion()).isEqualTo("1.2");
    }

    @Test
    void ignoresLegacyVisualGroupDuringRollingDeploy() {
        Map<String, Object> body = Map.of(
                "schemaVersion", "1.2",
                "stt", Map.of("language", "ko", "segments", List.of()),
                "ocr", Map.of("segments", List.of()),
                "visual", Map.of("segments", List.of()));

        assertThat(mapper.isCurrentExtraction(body)).isTrue();
        assertThat(mapper.fromBody(body).stt().language()).isEqualTo("ko");
    }

    @Test
    void rejectsLegacyTextBody() {
        assertThat(mapper.isCurrentExtraction(Map.of("text", "legacy"))).isFalse();
    }

    @Test
    void 검수_입력에서는_segmentId와_text만_남긴다() {
        Map<String, Object> body = mapper.toBody(new ContentMediaExtractionResult(
                "1.2",
                new ContentMediaExtractionResult.SttExtraction("ko", List.of(
                        new ContentMediaExtractionResult.SttSegment(
                                "stt-001", 100L, 900L, "발화"))),
                new ContentMediaExtractionResult.OcrExtraction(List.of(
                        new ContentMediaExtractionResult.OcrSegment(
                                "ocr-001", 200L, 800L, "화면 글자",
                                CoordinateSpace.NORMALIZED,
                                new NormalizedBoundingBox(0.1, 0.2, 0.3, 0.4))))));

        Map<String, Object> inspectionBody = mapper.toInspectionBody(body);

        assertThat(inspectionBody).containsEntry("schemaVersion", "1.2");
        assertThat(inspectionBody.toString())
                .contains("stt-001", "발화", "ocr-001", "화면 글자")
                .doesNotContain("startMs", "endMs", "bbox", "coordinateSpace", "report");
        assertThat(mapper.reportFrom(body).hasNoContent()).isTrue();
        assertThat(mapper.toInspectionBody(Map.of("text", "본문")))
                .containsEntry("text", "본문");
    }

    @Test
    void 저장된_리포트는_검수_입력에서_빼고_조회할_수_있다() {
        ContentReportAnalysis report = new ContentReportAnalysis(
                new ContentReportAnalysis.Overview("요약", "목적", "전개", "평가"),
                ContentReportAnalysis.Insight.empty());
        Map<String, Object> body = mapper.toBody(new ContentMediaExtractionResult(
                "1.2",
                ContentMediaExtractionResult.SttExtraction.empty(),
                ContentMediaExtractionResult.OcrExtraction.empty(),
                report));

        assertThat(mapper.reportFrom(body).overview().summary()).isEqualTo("요약");
        assertThat(mapper.toInspectionBody(body)).doesNotContainKey("report");
    }
}
