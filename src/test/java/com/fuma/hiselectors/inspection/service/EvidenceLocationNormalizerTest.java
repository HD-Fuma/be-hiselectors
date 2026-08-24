package com.fuma.hiselectors.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.inspection.model.DetectedViolation;
import com.fuma.hiselectors.inspection.model.EvidenceLocation;
import com.fuma.hiselectors.inspection.model.EvidenceSource;
import com.fuma.hiselectors.inspection.model.InspectionContext;
import com.fuma.hiselectors.inspection.model.ViolationEvidence;
import com.fuma.hiselectors.inspection.model.ViolationItem;
import com.fuma.hiselectors.inspection.model.ViolationTypeCode;
import com.fuma.hiselectors.selectors.model.Selectors;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EvidenceLocationNormalizerTest {

    private final EvidenceLocationNormalizer normalizer = new EvidenceLocationNormalizer();

    @Test
    void removesOnlyInvalidAiLocationAndKeepsViolation() {
        ContentMedia text = media(11L, MediaType.TEXT, 0, "정상 본문");
        EvidenceLocation invalid = new EvidenceLocation(
                999L, MediaType.TEXT, 0, 2, null, null, null, "정상");
        DetectedViolation violation = violation(EvidenceSource.AI, List.of(invalid));

        List<DetectedViolation> result = normalizer.normalize(
                context(List.of(text)), List.of(violation));

        assertThat(result).singleElement();
        assertThat(result.getFirst().evidence().locations()).isEmpty();
        assertThat(result.getFirst().evidence().source()).isEqualTo(EvidenceSource.AI);
    }

    @Test
    void sortsLocationsByMediaPriorityAndUsesFirstAsRepresentative() {
        ContentMedia image = media(13L, MediaType.IMAGE, 0, "화면");
        ContentMedia video = media(12L, MediaType.VIDEO, 9, "음성");
        ContentMedia text = media(11L, MediaType.TEXT, 20, "본문 링크");
        List<EvidenceLocation> locations = List.of(
                marker(image, "이미지"),
                marker(video, "영상"),
                new EvidenceLocation(11L, MediaType.TEXT, 0, 2,
                        null, null, null, "본문"));

        DetectedViolation normalized = normalizer.normalize(
                context(List.of(image, video, text)),
                List.of(violation(EvidenceSource.AI, locations))).getFirst();
        ViolationItem item = ViolationItem.pending(
                context(List.of()).version(), 100L, normalized.evidence());

        assertThat(normalized.evidence().locations())
                .extracting(EvidenceLocation::contentMediaId)
                .containsExactly(11L, 12L, 13L);
        assertThat(item.getContentMediaId()).isEqualTo(11L);
    }

    @Test
    void keepsCoordinateLessRuleMediaMarker() {
        ContentMedia video = media(12L, MediaType.VIDEO, 0, "영상 본문");

        DetectedViolation normalized = normalizer.normalize(
                context(List.of(video)),
                List.of(violation(EvidenceSource.RULE,
                        List.of(marker(video, "영상 내부 광고 안내 문구")))))
                .getFirst();

        assertThat(normalized.evidence().locations()).singleElement().satisfies(location -> {
            assertThat(location.contentMediaId()).isEqualTo(12L);
            assertThat(location.startIndex()).isNull();
            assertThat(location.startTime()).isNull();
            assertThat(location.bbox()).isNull();
        });
    }

    private DetectedViolation violation(
            EvidenceSource source, List<EvidenceLocation> locations) {
        return new DetectedViolation(
                ViolationTypeCode.ABUSIVE_LANGUAGE,
                new ViolationEvidence("근거", 0.9, locations, source));
    }

    private EvidenceLocation marker(ContentMedia media, String excerpt) {
        return new EvidenceLocation(media.getId(), media.getMediaType(),
                null, null, null, null, null, excerpt);
    }

    private InspectionContext context(List<ContentMedia> media) {
        Content content = Content.create(
                1L, SnsPlatform.YOUTUBE, "https://youtu.be/abc123", "LONG_FORM");
        ReflectionTestUtils.setField(content, "id", 10L);
        ContentVersion version = ContentVersion.create(10L, 1L, "hash");
        ReflectionTestUtils.setField(version, "id", 20L);
        return new InspectionContext(
                content, version,
                Selectors.builder().selectorsRoleId("SELECTORS")
                        .selectorsCode("SEL-1").build(),
                media);
    }

    private ContentMedia media(Long id, MediaType type, int sequenceNo, String text) {
        ContentMedia media = ContentMedia.create(
                20L, type, null, "sns-" + id, sequenceNo, Map.of("text", text));
        ReflectionTestUtils.setField(media, "id", id);
        return media;
    }
}
