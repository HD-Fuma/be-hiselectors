package com.fuma.hiselectors.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;

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
                "1.0",
                new ContentMediaExtractionResult.SttExtraction("ko", List.of(
                        new ContentMediaExtractionResult.SttSegment(
                                "stt-001", 0L, 500L, "speech"))),
                new ContentMediaExtractionResult.OcrExtraction(List.of(
                        new ContentMediaExtractionResult.OcrSegment(
                                "ocr-001", null, null, "screen text",
                                CoordinateSpace.NORMALIZED,
                                new NormalizedBoundingBox(0.1, 0.2, 0.3, 0.4)))),
                ContentMediaExtractionResult.VisualExtraction.empty());

        Map<String, Object> body = mapper.toBody(extraction);

        assertThat(body).containsKeys("schemaVersion", "stt", "ocr", "visual");
        assertThat(mapper.isCurrentExtraction(body)).isTrue();
        assertThat(mapper.fromBody(body)).isEqualTo(extraction);
    }

    @Test
    void rejectsLegacyTextBody() {
        assertThat(mapper.isCurrentExtraction(Map.of("text", "legacy"))).isFalse();
    }
}
