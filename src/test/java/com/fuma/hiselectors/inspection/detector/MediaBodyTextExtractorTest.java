package com.fuma.hiselectors.inspection.detector;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.inspection.model.EvidenceTargetKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MediaBodyTextExtractorTest {

    @Test
    void extractsOnlyEvidenceTextWithItsSegmentIdentity() {
        ContentMedia video = ContentMedia.create(
                20L, MediaType.VIDEO, null, null, "video-id", 0, Map.of(
                        "schemaVersion", "1.2",
                        "stt", Map.of(
                                "language", "ko",
                                "segments", List.of(Map.of(
                                        "segmentId", "stt-001",
                                        "startMs", 0,
                                        "endMs", 500,
                                        "text", "spoken evidence"))),
                        "ocr", Map.of("segments", List.of(Map.of(
                                "segmentId", "ocr-001",
                                "text", "screen evidence")))));
        ReflectionTestUtils.setField(video, "id", 30L);

        var sources = new MediaBodyTextExtractor().extract(List.of(video));

        assertThat(sources)
                .extracting(source -> source.targetKind() + ":" + source.segmentId())
                .containsExactly(
                        EvidenceTargetKind.STT_SEGMENT + ":stt-001",
                        EvidenceTargetKind.OCR_SEGMENT + ":ocr-001");
        assertThat(sources).extracting(MediaBodyTextExtractor.TextSource::text)
                .containsExactly("spoken evidence", "screen evidence")
                .doesNotContain("ko", "stt-001", "NORMALIZED");
    }
}
