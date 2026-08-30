package com.fuma.hiselectors.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.inspection.extraction.model.ContentMediaExtractionResult;
import com.fuma.hiselectors.inspection.extraction.model.CoordinateSpace;
import com.fuma.hiselectors.inspection.extraction.model.NormalizedBoundingBox;
import com.fuma.hiselectors.inspection.model.DetectedViolation;
import com.fuma.hiselectors.inspection.model.EvidenceCoordinateSpace;
import com.fuma.hiselectors.inspection.model.EvidenceLocation;
import com.fuma.hiselectors.inspection.model.EvidenceSource;
import com.fuma.hiselectors.inspection.model.EvidenceTargetKind;
import com.fuma.hiselectors.inspection.model.InspectionContext;
import com.fuma.hiselectors.inspection.model.ViolationEvidence;
import com.fuma.hiselectors.inspection.model.ViolationItem;
import com.fuma.hiselectors.inspection.model.ViolationTypeCode;
import com.fuma.hiselectors.selectors.model.Selectors;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class EvidenceLocationNormalizerTest {

    private final ContentMediaExtractionBodyMapper bodyMapper =
            new ContentMediaExtractionBodyMapper(new ObjectMapper());
    private final EvidenceLocationNormalizer normalizer =
            new EvidenceLocationNormalizer(bodyMapper);

    @Test
    void removesOnlyInvalidAiLocationAndKeepsViolation() {
        ContentMedia text = textMedia(11L, 0, "normal body");
        EvidenceLocation invalid = textLocation(999L, 0, 6, "normal");
        DetectedViolation violation = violation(EvidenceSource.AI, List.of(invalid));

        List<DetectedViolation> result = normalizer.normalize(
                context(List.of(text)), List.of(violation));

        assertThat(result).singleElement();
        assertThat(result.getFirst().evidence().locations()).isEmpty();
        assertThat(result.getFirst().evidence().source()).isEqualTo(EvidenceSource.AI);
    }

    @Test
    void validatesStoredSegmentsAndSortsByExplicitTargetKind() {
        ContentMedia image = extractedMedia(13L, MediaType.IMAGE, 0, extraction(
                null, new ContentMediaExtractionResult.OcrSegment(
                        "ocr-001", null, null, "screen text",
                        CoordinateSpace.NORMALIZED,
                        new NormalizedBoundingBox(0.1, 0.2, 0.3, 0.1))));
        ContentMedia video = extractedMedia(12L, MediaType.VIDEO, 9, extraction(
                new ContentMediaExtractionResult.SttSegment(
                        "stt-001", 100L, 900L, "spoken evidence"), null));
        ContentMedia text = textMedia(11L, 20, "body link");
        List<EvidenceLocation> locations = List.of(
                segmentLocation(image, EvidenceTargetKind.OCR_SEGMENT,
                        "ocr-001", "screen"),
                segmentLocation(video, EvidenceTargetKind.STT_SEGMENT,
                        "stt-001", "spoken"),
                textLocation(11L, 0, 4, "body"));

        DetectedViolation normalized = normalizer.normalize(
                context(List.of(image, video, text)),
                List.of(violation(EvidenceSource.AI, locations))).getFirst();
        ViolationItem item = ViolationItem.pending(
                context(List.of()).version(), 100L, normalized.evidence());

        assertThat(normalized.evidence().locations())
                .extracting(EvidenceLocation::targetKind)
                .containsExactly(
                        EvidenceTargetKind.TEXT_BODY,
                        EvidenceTargetKind.STT_SEGMENT,
                        EvidenceTargetKind.OCR_SEGMENT);
        assertThat(item.getContentMediaId()).isEqualTo(11L);
    }

    @Test
    void removesAiLocationThatReferencesMissingSegment() {
        ContentMedia video = extractedMedia(12L, MediaType.VIDEO, 0, extraction(
                new ContentMediaExtractionResult.SttSegment(
                        "stt-001", 0L, 500L, "stored speech"), null));
        EvidenceLocation fabricated = segmentLocation(
                video, EvidenceTargetKind.STT_SEGMENT, "stt-999", "speech");

        DetectedViolation normalized = normalizer.normalize(
                context(List.of(video)),
                List.of(violation(EvidenceSource.AI, List.of(fabricated)))).getFirst();

        assertThat(normalized.evidence().locations()).isEmpty();
    }

    @Test
    void dropsLegacyVisualSegmentFromAiEvidence() {
        ContentMedia video = extractedMedia(
                12L, MediaType.VIDEO, 0, ContentMediaExtractionResult.empty());
        EvidenceLocation visual = segmentLocation(
                video, EvidenceTargetKind.VISUAL_SEGMENT, "visual-001", "장면");

        DetectedViolation normalized = normalizer.normalize(
                context(List.of(video)),
                List.of(violation(EvidenceSource.AI, List.of(visual)))).getFirst();

        assertThat(normalized.evidence().locations()).isEmpty();
    }

    @Test
    void keepsCoordinateLessRuleMediaMarker() {
        ContentMedia video = extractedMedia(
                12L, MediaType.VIDEO, 0, ContentMediaExtractionResult.empty());
        EvidenceLocation marker = new EvidenceLocation(
                12L, MediaType.VIDEO, EvidenceTargetKind.MEDIA,
                EvidenceCoordinateSpace.NONE, null, null, null,
                "missing ad disclosure");

        DetectedViolation normalized = normalizer.normalize(
                context(List.of(video)),
                List.of(violation(EvidenceSource.RULE, List.of(marker))))
                .getFirst();

        assertThat(normalized.evidence().locations()).singleElement().satisfies(location -> {
            assertThat(location.targetKind()).isEqualTo(EvidenceTargetKind.MEDIA);
            assertThat(location.coordinateSpace()).isEqualTo(EvidenceCoordinateSpace.NONE);
            assertThat(location.segmentId()).isNull();
            assertThat(location.startIndex()).isNull();
        });
    }

    @Test
    void readsLegacyEvidenceJsonWithoutFailingHistoricalQueries() throws Exception {
        EvidenceLocation legacy = new ObjectMapper().readValue("""
                {
                  "contentMediaId": 12,
                  "mediaType": "VIDEO",
                  "startTime": 1.5,
                  "endTime": 2.5,
                  "excerpt": "legacy evidence"
                }
                """, EvidenceLocation.class);

        assertThat(legacy.contentMediaId()).isEqualTo(12L);
        assertThat(legacy.targetKind()).isNull();
        assertThat(legacy.excerpt()).isEqualTo("legacy evidence");
    }

    private DetectedViolation violation(
            EvidenceSource source, List<EvidenceLocation> locations) {
        return new DetectedViolation(
                ViolationTypeCode.ABUSIVE_LANGUAGE,
                new ViolationEvidence("reason", 0.9, locations, source));
    }

    private EvidenceLocation textLocation(
            Long mediaId, int startIndex, int endIndex, String excerpt) {
        return new EvidenceLocation(
                mediaId, MediaType.TEXT, EvidenceTargetKind.TEXT_BODY,
                EvidenceCoordinateSpace.UTF16_CODE_UNIT, null,
                startIndex, endIndex, excerpt);
    }

    private EvidenceLocation segmentLocation(
            ContentMedia media,
            EvidenceTargetKind targetKind,
            String segmentId,
            String excerpt) {
        return new EvidenceLocation(
                media.getId(), media.getMediaType(), targetKind,
                EvidenceCoordinateSpace.CONTENT_MEDIA_SEGMENT,
                segmentId, null, null, excerpt);
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

    private ContentMedia textMedia(Long id, int sequenceNo, String text) {
        ContentMedia media = ContentMedia.create(
                20L, MediaType.TEXT, null, null, "sns-" + id,
                sequenceNo, Map.of("text", text));
        ReflectionTestUtils.setField(media, "id", id);
        return media;
    }

    private ContentMedia extractedMedia(
            Long id, MediaType type, int sequenceNo,
            ContentMediaExtractionResult extraction) {
        ContentMedia media = ContentMedia.create(
                20L, type, "https://cdn/" + id, null, "sns-" + id,
                sequenceNo, bodyMapper.toBody(extraction));
        ReflectionTestUtils.setField(media, "id", id);
        return media;
    }

    private ContentMediaExtractionResult extraction(
            ContentMediaExtractionResult.SttSegment stt,
            ContentMediaExtractionResult.OcrSegment ocr) {
        return new ContentMediaExtractionResult(
                "1.2",
                new ContentMediaExtractionResult.SttExtraction(
                        "ko", stt == null ? List.of() : List.of(stt)),
                new ContentMediaExtractionResult.OcrExtraction(
                        ocr == null ? List.of() : List.of(ocr)));
    }
}
