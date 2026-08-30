package com.fuma.hiselectors.inspection.detector;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.inspection.config.ContentInspectionProperties;
import com.fuma.hiselectors.inspection.model.EvidenceCoordinateSpace;
import com.fuma.hiselectors.inspection.model.EvidenceSource;
import com.fuma.hiselectors.inspection.model.EvidenceTargetKind;
import com.fuma.hiselectors.inspection.model.InspectionContext;
import com.fuma.hiselectors.selectors.model.Selectors;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class AdvertisementDisclosureDetectorTest {

    @Test
    void detectsMissingDisclosure() {
        AdvertisementDisclosureDetector detector = new AdvertisementDisclosureDetector(
                new MediaBodyTextExtractor(), new ContentInspectionProperties(null, null, null));
        Content content = Content.create(1L, SnsPlatform.INSTAGRAM,
                "https://instagram.com/p/1", "POST");
        ContentVersion version = ContentVersion.create(10L, 1L, "hash");
        ContentMedia media = ContentMedia.create(20L, null, MediaType.TEXT,
                Map.of("text", "오늘 추천하는 향수입니다."));

        var result = detector.detect(new InspectionContext(
                content, version, selectors(), List.of(media)));

        assertThat(result).singleElement()
                .satisfies(violation -> {
                    assertThat(violation.evidence().confidence()).isEqualTo(1.0);
                    assertThat(violation.evidence().source()).isEqualTo(EvidenceSource.RULE);
                    assertThat(violation.evidence().locations()).singleElement()
                            .satisfies(location -> {
                                assertThat(location.mediaType()).isEqualTo(MediaType.TEXT);
                                assertThat(location.targetKind())
                                        .isEqualTo(EvidenceTargetKind.MEDIA);
                                assertThat(location.coordinateSpace())
                                        .isEqualTo(EvidenceCoordinateSpace.NONE);
                                assertThat(location.startIndex()).isNull();
                                assertThat(location.endIndex()).isNull();
                                assertThat(location.excerpt()).isEqualTo("제목 또는 본문 첫 줄");
                            });
                    assertThat(violation.evidence().reason())
                            .isEqualTo("제목 또는 본문 첫 줄의 광고·수수료 안내 문구 및 영상 내부 광고 안내 문구를 확인할 수 없습니다.");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "본 콘텐츠는 더현대Hi 셀렉터스 활동의 일환으로, 링크를 통해 구매가 발생할 경우 일정 수수료를 제공받습니다.",
            "본 콘텐츠는 더현대Hi 셀렉터스 활동의 일환으로, 셀렉터스샵을 통해 구매가 발생할 경우 일정 수수료를 제공받습니다."
    })
    void acceptsSelectorsDisclosureRegardlessOfCommaAndPeriod(String disclosure) {
        AdvertisementDisclosureDetector detector = new AdvertisementDisclosureDetector(
                new MediaBodyTextExtractor(), new ContentInspectionProperties(null, null, null));
        Content content = Content.create(1L, SnsPlatform.INSTAGRAM,
                "https://instagram.com/p/1", "POST");
        ContentVersion version = ContentVersion.create(10L, 1L, "hash");
        ContentMedia media = ContentMedia.create(20L, null, MediaType.TEXT,
                Map.of("text", disclosure));

        var result = detector.detect(new InspectionContext(
                content, version, selectors(), List.of(media)));

        assertThat(result).isEmpty();
    }

    @Test
    void acceptsTitleDisclosureWithoutVideoOrImageText() {
        var result = detector().detect(context(
                SnsPlatform.YOUTUBE,
                textMedia("광고\n종이컵이 8개"),
                videoMedia("종이컵을 소개합니다.", "화면 자막")));

        assertThat(result).isEmpty();
    }

    @Test
    void acceptsVideoSttDisclosureWithoutTitleOrFirstLine() {
        var result = detector().detect(context(
                SnsPlatform.YOUTUBE,
                textMedia("종이컵이 8개"),
                videoMedia("이 영상은 광고입니다.", "화면 자막")));

        assertThat(result).isEmpty();
    }

    @Test
    void acceptsImageOcrDisclosureWithoutTitleOrFirstLine() {
        var result = detector().detect(context(
                SnsPlatform.INSTAGRAM,
                textMedia("종이컵이 8개"),
                imageMedia("유료광고"),
                imageMedia("종이컵 8개")));

        assertThat(result).isEmpty();
    }

    @Test
    void reportsSingleContentLevelViolationWhenEverySourceMissesDisclosure() {
        var result = detector().detect(context(
                SnsPlatform.YOUTUBE,
                textMedia("종이컵이 8개\n더현대 셀렉터스"),
                videoMedia("종이컵을 소개합니다.", "종이컵 8개"),
                imageMedia("썸네일 문구")));

        assertThat(result).singleElement()
                .satisfies(violation -> {
                    assertThat(violation.evidence().locations()).singleElement()
                            .satisfies(location -> {
                                assertThat(location.contentMediaId()).isEqualTo(21L);
                                assertThat(location.mediaType()).isEqualTo(MediaType.TEXT);
                                assertThat(location.targetKind())
                                        .isEqualTo(EvidenceTargetKind.MEDIA);
                                assertThat(location.startIndex()).isNull();
                                assertThat(location.endIndex()).isNull();
                            });
                });
    }

    @Test
    void marksVideoWhenTextMediaIsMissing() {
        var result = detector().detect(context(
                SnsPlatform.YOUTUBE,
                videoMedia("종이컵을 소개합니다.", "종이컵 8개")));

        assertThat(result).singleElement()
                .satisfies(violation -> assertThat(violation.evidence().locations())
                        .singleElement()
                        .satisfies(location -> {
                            assertThat(location.contentMediaId()).isEqualTo(22L);
                            assertThat(location.mediaType()).isEqualTo(MediaType.VIDEO);
                            assertThat(location.targetKind()).isEqualTo(EvidenceTargetKind.MEDIA);
                        }));
    }

    private AdvertisementDisclosureDetector detector() {
        return new AdvertisementDisclosureDetector(
                new MediaBodyTextExtractor(), new ContentInspectionProperties(null, null, null));
    }

    private InspectionContext context(SnsPlatform platform, ContentMedia... media) {
        Content content = Content.create(1L, platform,
                platform == SnsPlatform.YOUTUBE
                        ? "https://youtu.be/id" : "https://instagram.com/p/1",
                platform == SnsPlatform.YOUTUBE ? "LONG_FORM" : "POST");
        return new InspectionContext(
                content, ContentVersion.create(10L, 1L, "hash"), selectors(), List.of(media));
    }

    private ContentMedia textMedia(String text) {
        return mediaWithId(21L, ContentMedia.create(
                20L, null, MediaType.TEXT, Map.of("text", text)));
    }

    private ContentMedia videoMedia(String stt, String ocr) {
        return mediaWithId(22L, ContentMedia.create(
                20L, MediaType.VIDEO, null, null, "video-id", 0, Map.of(
                        "schemaVersion", "1.2",
                        "stt", Map.of("segments", List.of(Map.of(
                                "segmentId", "stt-001", "text", stt))),
                        "ocr", Map.of("segments", List.of(Map.of(
                                "segmentId", "ocr-001", "text", ocr))))));
    }

    private ContentMedia imageMedia(String ocr) {
        return mediaWithId(23L, ContentMedia.create(
                20L, MediaType.IMAGE, null, null, "image-id", 1, Map.of(
                        "schemaVersion", "1.2",
                        "ocr", Map.of("segments", List.of(Map.of(
                                "segmentId", "ocr-001", "text", ocr))))));
    }

    private ContentMedia mediaWithId(Long id, ContentMedia media) {
        ReflectionTestUtils.setField(media, "id", id);
        return media;
    }

    private Selectors selectors() {
        return Selectors.builder().selectorsRoleId("SELECTORS").selectorsCode("SEL-1").build();
    }
}
