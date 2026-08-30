package com.fuma.hiselectors.content.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentReportAnalysis;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.inspection.extraction.model.ContentMediaExtractionResult;
import com.fuma.hiselectors.inspection.service.ContentMediaExtractionBodyMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class ContentVersionMediaResponseTest {

    @Test
    void 추출_body의_report와_null값은_상세_응답에서_뺀다() {
        Map<String, Object> body = new ContentMediaExtractionBodyMapper(new ObjectMapper())
                .toBody(new ContentMediaExtractionResult(
                        "1.2",
                        new ContentMediaExtractionResult.SttExtraction("ko", java.util.List.of(
                                new ContentMediaExtractionResult.SttSegment(
                                        "stt-001", 0L, 1_000L, "발화"))),
                        ContentMediaExtractionResult.OcrExtraction.empty(),
                        new ContentReportAnalysis(
                                new ContentReportAnalysis.Overview("요약", "", "", ""),
                                ContentReportAnalysis.Insight.empty())));
        body = new HashMap<>(body);
        body.put("unused", null);
        ContentMedia media = ContentMedia.create(
                1L, MediaType.VIDEO, "https://cdn/video", "vid", 0, body);
        ReflectionTestUtils.setField(media, "id", 11L);

        ContentVersionMediaResponse response = ContentVersionMediaResponse.from(media);

        assertThat(response.body())
                .containsKeys("schemaVersion", "stt", "ocr")
                .doesNotContainKey("report")
                .doesNotContainKey("unused");
        assertThat(response.text()).isNull();
    }
}
