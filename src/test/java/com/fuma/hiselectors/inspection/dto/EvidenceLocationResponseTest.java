package com.fuma.hiselectors.inspection.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.inspection.extraction.model.CoordinateSpace;
import com.fuma.hiselectors.inspection.extraction.model.NormalizedBoundingBox;
import com.fuma.hiselectors.inspection.model.EvidenceCoordinateSpace;
import com.fuma.hiselectors.inspection.model.EvidenceLocation;
import com.fuma.hiselectors.inspection.model.EvidenceTargetKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EvidenceLocationResponseTest {

    @Test
    void resolvesOcrTimestampAndNormalizedBoundingBoxFromMediaBody() {
        ContentMedia image = ContentMedia.create(
                20L, MediaType.IMAGE, "https://cdn/image.jpg", "image-id", 0, Map.of(
                        "schemaVersion", "1.0",
                        "stt", Map.of("language", "", "segments", List.of()),
                        "ocr", Map.of("segments", List.of(Map.of(
                                "segmentId", "ocr-001",
                                "startMs", 200,
                                "endMs", 800,
                                "text", "screen text",
                                "coordinateSpace", "NORMALIZED",
                                "bbox", Map.of(
                                        "x", 0.1,
                                        "y", 0.2,
                                        "width", 0.3,
                                        "height", 0.1)))),
                        "visual", Map.of("segments", List.of())));
        ReflectionTestUtils.setField(image, "id", 30L);
        EvidenceLocation location = new EvidenceLocation(
                30L, MediaType.IMAGE, EvidenceTargetKind.OCR_SEGMENT,
                EvidenceCoordinateSpace.CONTENT_MEDIA_SEGMENT,
                "ocr-001", null, null, "screen");

        EvidenceLocationResponse response = EvidenceLocationResponse.from(location, image);

        assertThat(response.startMs()).isEqualTo(200L);
        assertThat(response.endMs()).isEqualTo(800L);
        assertThat(response.bboxCoordinateSpace()).isEqualTo(CoordinateSpace.NORMALIZED);
        assertThat(response.bbox()).isEqualTo(
                new NormalizedBoundingBox(0.1, 0.2, 0.3, 0.1));
    }
}
