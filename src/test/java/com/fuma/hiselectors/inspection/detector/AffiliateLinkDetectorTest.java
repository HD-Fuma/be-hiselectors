package com.fuma.hiselectors.inspection.detector;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.inspection.model.InspectionContext;
import com.fuma.hiselectors.inspection.model.EvidenceSource;
import com.fuma.hiselectors.inspection.model.EvidenceCoordinateSpace;
import com.fuma.hiselectors.inspection.model.EvidenceTargetKind;
import com.fuma.hiselectors.selectors.model.Selectors;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AffiliateLinkDetectorTest {

    @Test
    void detectsInvalidAffiliateLink() {
        AffiliateLinkDetector detector = new AffiliateLinkDetector(
                new MediaBodyTextExtractor(), url -> false);
        ContentMedia media = ContentMedia.create(20L, null, MediaType.TEXT,
                Map.of("text", "상품 링크 https://example.com/product?id=1"));
        InspectionContext context = new InspectionContext(
                Content.create(1L, SnsPlatform.INSTAGRAM, "https://instagram.com/p/1", "POST"),
                ContentVersion.create(10L, 1L, "hash"),
                Selectors.builder().selectorsRoleId("SELECTORS").selectorsCode("SEL-1").build(),
                List.of(media));

        var result = detector.detect(context);

        assertThat(result).singleElement()
                .satisfies(violation -> {
                    assertThat(violation.evidence().source()).isEqualTo(EvidenceSource.RULE);
                    assertThat(violation.evidence().locations()).singleElement()
                            .satisfies(location -> {
                                assertThat(location.startIndex()).isEqualTo(6);
                                assertThat(location.endIndex()).isEqualTo(38);
                            });
                });
    }

    @Test
    void referencesStructuredSegmentInsteadOfInventingTextCoordinates() {
        AffiliateLinkDetector detector = new AffiliateLinkDetector(
                new MediaBodyTextExtractor(), url -> false);
        ContentMedia media = ContentMedia.create(
                20L, MediaType.VIDEO, null, null, "video-id", 0, Map.of(
                        "schemaVersion", "1.2",
                        "stt", Map.of(
                                "language", "ko",
                                "segments", List.of(Map.of(
                                        "segmentId", "stt-001",
                                        "startMs", 0,
                                        "endMs", 500,
                                        "text", "link https://example.com/product"))),
                        "ocr", Map.of("segments", List.of())));
        InspectionContext context = new InspectionContext(
                Content.create(1L, SnsPlatform.YOUTUBE, "https://youtu.be/id", "LONG_FORM"),
                ContentVersion.create(10L, 1L, "hash"),
                Selectors.builder().selectorsRoleId("SELECTORS")
                        .selectorsCode("SEL-1").build(),
                List.of(media));

        var location = detector.detect(context).getFirst()
                .evidence().locations().getFirst();

        assertThat(location.targetKind()).isEqualTo(EvidenceTargetKind.STT_SEGMENT);
        assertThat(location.coordinateSpace())
                .isEqualTo(EvidenceCoordinateSpace.CONTENT_MEDIA_SEGMENT);
        assertThat(location.segmentId()).isEqualTo("stt-001");
        assertThat(location.startIndex()).isNull();
        assertThat(location.endIndex()).isNull();
    }

    @Test
    void marksTextStartWhenNoAffiliateLinkExists() {
        AffiliateLinkDetector detector = new AffiliateLinkDetector(
                new MediaBodyTextExtractor(), url -> false);
        ContentMedia media = ContentMedia.create(20L, null, MediaType.TEXT,
                Map.of("text", "종이컵이 8개"));
        ReflectionTestUtils.setField(media, "id", 21L);
        InspectionContext context = new InspectionContext(
                Content.create(1L, SnsPlatform.INSTAGRAM, "https://instagram.com/p/1", "POST"),
                ContentVersion.create(10L, 1L, "hash"),
                Selectors.builder().selectorsRoleId("SELECTORS").selectorsCode("SEL-1").build(),
                List.of(media));

        var location = detector.detect(context).getFirst().evidence().locations().getFirst();

        assertThat(location.contentMediaId()).isEqualTo(21L);
        assertThat(location.mediaType()).isEqualTo(MediaType.TEXT);
        assertThat(location.targetKind()).isEqualTo(EvidenceTargetKind.MEDIA);
        assertThat(location.coordinateSpace()).isEqualTo(EvidenceCoordinateSpace.NONE);
        assertThat(location.startIndex()).isNull();
        assertThat(location.endIndex()).isNull();
        assertThat(location.excerpt()).isEqualTo("제휴 링크");
    }
}
