package com.fuma.hiselectors.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.client.InstagramContentFetcher;
import com.fuma.hiselectors.content.client.YoutubeContentFetcher;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.extraction.ContentExtractionExecutionResult;
import com.fuma.hiselectors.inspection.extraction.InstagramContentExtractionClient;
import com.fuma.hiselectors.inspection.extraction.YoutubeContentExtractionClient;
import com.fuma.hiselectors.inspection.extraction.model.ContentMediaExtractionResult;
import com.fuma.hiselectors.inspection.model.InspectionPolicy;
import com.fuma.hiselectors.inspection.repository.InspectionPolicyRepository;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class MediaPreprocessingServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-21T03:00:00Z"), ZoneOffset.UTC);

    private final YoutubeContentExtractionClient youtube =
            mock(YoutubeContentExtractionClient.class);
    private final InstagramContentExtractionClient instagram =
            mock(InstagramContentExtractionClient.class);
    private final YoutubeContentFetcher youtubeFetcher = mock(YoutubeContentFetcher.class);
    private final InstagramContentFetcher instagramFetcher = mock(InstagramContentFetcher.class);
    private final SelectorsSnsAccountRepository accounts =
            mock(SelectorsSnsAccountRepository.class);
    private final InspectionPolicyRepository policies = mock(InspectionPolicyRepository.class);
    private final ContentMediaExtractionBodyMapper bodyMapper =
            new ContentMediaExtractionBodyMapper(new ObjectMapper());

    @Test
    void reusesCurrentYoutubeExtractionWhenInputAndExtractionConfigAreSame() {
        InspectionPolicy previous = policy(1L, "same-extraction");
        InspectionPolicy active = policy(2L, "same-extraction");
        ContentMedia video = video(0, bodyMapper.toBody(extraction("stt-001")));
        video.markExtracted(
                1L, sha256("YOUTUBE\nVIDEO\nabc123"),
                CLOCK.instant().atOffset(ZoneOffset.UTC).toLocalDateTime());
        when(policies.findById(1L)).thenReturn(Optional.of(previous));

        MediaPreprocessingService.PreprocessingResult result = service().preprocess(
                youtubeContent(), List.of(video), active);

        assertThat(result.extractionUpdates()).isEmpty();
        verify(youtube, never()).extract(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(video.getExtractedWithPolicyId()).isEqualTo(1L);
        assertThat(video.getBody()).containsEntry("schemaVersion", "1.2");
    }

    @Test
    void savesStructuredYoutubeExtractionInMediaBody() {
        ContentMedia video = video(0, Map.of());
        when(youtubeFetcher.durationMs("abc123")).thenReturn(618_000L);
        when(youtube.extract("abc123", 618_000L)).thenReturn(execution(extraction("stt-001")));

        MediaPreprocessingService.PreprocessingResult result = service().preprocess(
                youtubeContent(), List.of(video), policy(2L, "new-extraction"));

        assertThat(result.extractionUpdates()).hasSize(1);
        assertThat(video.getBody()).containsKeys("schemaVersion", "stt", "ocr")
                .doesNotContainKey("visual");
        assertThat(video.getBody()).doesNotContainKey("text");
        assertThat(video.getExtractedWithPolicyId()).isEqualTo(2L);
        assertThat(video.getExtractionInputHash())
                .isEqualTo(sha256("YOUTUBE\nVIDEO\nabc123"));
    }

    @Test
    void legacyBodyRequiresExtractionChangeVersion() {
        assertThat(service().requiresNewVersion(
                youtubeContent(),
                List.of(video(0, Map.of("text", "legacy transcript"))),
                policy(2L, "new-extraction")))
                .isTrue();
    }

    @Test
    void extractsEveryInstagramMediaInSequenceOrder() {
        ContentMedia first = instagramMedia(
                MediaType.IMAGE, 0, "https://cdn/first.jpg", null);
        ContentMedia second = instagramMedia(
                MediaType.VIDEO, 1, "https://cdn/second.mp4", "https://cdn/thumb.jpg");
        when(instagram.extract("https://cdn/first.jpg", null))
                .thenReturn(execution(extraction("ocr-001")));
        when(instagram.extract("https://cdn/second.mp4", "https://cdn/thumb.jpg"))
                .thenReturn(execution(extraction("stt-002")));

        MediaPreprocessingService.PreprocessingResult result = service().preprocess(
                instagramContent(), List.of(second, first), policy(3L, "instagram-v1"));

        assertThat(result.extractionUpdates()).hasSize(2);
        InOrder order = inOrder(instagram);
        order.verify(instagram).extract("https://cdn/first.jpg", null);
        order.verify(instagram).extract("https://cdn/second.mp4", "https://cdn/thumb.jpg");
        assertThat(first.getBody()).containsEntry("schemaVersion", "1.2");
        assertThat(second.getBody()).containsEntry("schemaVersion", "1.2");
    }

    @Test
    void failsWholeInspectionWhenInstagramVideoSourceIsMissing() {
        ContentMedia video = instagramMedia(
                MediaType.VIDEO, 0, null, "https://cdn/thumb.jpg");

        assertThatThrownBy(() -> service().preprocess(
                instagramContent(), List.of(video), policy(3L, "instagram-v1")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.CONTENT_MEDIA_SOURCE_UNAVAILABLE));
        assertThat(video.getBody()).isEmpty();
        verify(instagram, never()).extract(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(instagramFetcher, never()).fetchMediaUrls(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void appliesNoMediaWhenAnyExtractionFails() {
        ContentMedia first = instagramMedia(
                MediaType.IMAGE, 0, "https://cdn/first.jpg", null);
        ContentMedia second = instagramMedia(
                MediaType.IMAGE, 1, "https://cdn/second.jpg", null);
        when(instagram.extract("https://cdn/first.jpg", null))
                .thenReturn(execution(extraction("ocr-001")));
        when(instagram.extract("https://cdn/second.jpg", null))
                .thenThrow(new BusinessException(ErrorCode.STT_WORKER_CALL_FAILED));

        assertThatThrownBy(() -> service().preprocess(
                instagramContent(), List.of(first, second), policy(3L, "instagram-v1")))
                .isInstanceOf(BusinessException.class);
        assertThat(first.getBody()).isEmpty();
        assertThat(second.getBody()).isEmpty();
        assertThat(first.getExtractedWithPolicyId()).isNull();
        assertThat(second.getExtractedWithPolicyId()).isNull();
    }

    @Test
    void refreshesExpiredInstagramUrlOnceAndRetriesExtraction() {
        stubInstagramAccount();
        ContentMedia video = instagramMedia(
                MediaType.VIDEO, 0, "https://cdn/expired.mp4", "https://cdn/old.jpg");
        when(instagram.extract("https://cdn/expired.mp4", "https://cdn/old.jpg"))
                .thenThrow(new BusinessException(ErrorCode.MEDIA_URL_EXPIRED));
        when(instagramFetcher.fetchMediaUrls("selector.insta", "post-1", "media-0"))
                .thenReturn(new InstagramContentFetcher.MediaUrls(
                        "https://cdn/fresh.mp4", "https://cdn/fresh.jpg"));
        when(instagram.extract("https://cdn/fresh.mp4", "https://cdn/fresh.jpg"))
                .thenReturn(execution(extraction("stt-001")));

        MediaPreprocessingService.PreprocessingResult result = service().preprocess(
                instagramContent(), List.of(video), policy(3L, "instagram-v1"));

        assertThat(result.extractionUpdates()).hasSize(1);
        assertThat(video.getMediaUrl()).isEqualTo("https://cdn/fresh.mp4");
        assertThat(video.getThumbnailUrl()).isEqualTo("https://cdn/fresh.jpg");
        assertThat(result.extractionUpdates().getFirst().mediaUrl())
                .isEqualTo("https://cdn/fresh.mp4");
        verify(instagramFetcher).fetchMediaUrls("selector.insta", "post-1", "media-0");
    }

    @Test
    void refreshesMissingInstagramVideoUrlBeforeExtraction() {
        stubInstagramAccount();
        ContentMedia video = instagramMedia(
                MediaType.VIDEO, 0, null, "https://cdn/thumb.jpg");
        when(instagramFetcher.fetchMediaUrls("selector.insta", "post-1", "media-0"))
                .thenReturn(new InstagramContentFetcher.MediaUrls(
                        "https://cdn/fresh.mp4", "https://cdn/fresh.jpg"));
        when(instagram.extract("https://cdn/fresh.mp4", "https://cdn/fresh.jpg"))
                .thenReturn(execution(extraction("stt-001")));

        MediaPreprocessingService.PreprocessingResult result = service().preprocess(
                instagramContent(), List.of(video), policy(3L, "instagram-v1"));

        assertThat(result.extractionUpdates()).hasSize(1);
        assertThat(video.getMediaUrl()).isEqualTo("https://cdn/fresh.mp4");
        verify(instagramFetcher).fetchMediaUrls("selector.insta", "post-1", "media-0");
    }

    @Test
    void doesNotRefreshWhenExpiredMediaHasNoSnsId() {
        ContentMedia image = ContentMedia.create(
                20L, MediaType.IMAGE, "https://cdn/expired.jpg", null, null, 0, Map.of());
        ReflectionTestUtils.setField(image, "id", 41L);
        when(instagram.extract("https://cdn/expired.jpg", null))
                .thenThrow(new BusinessException(ErrorCode.MEDIA_URL_EXPIRED));

        assertThatThrownBy(() -> service().preprocess(
                instagramContent(), List.of(image), policy(3L, "instagram-v1")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.MEDIA_URL_EXPIRED));
        verify(instagramFetcher, never()).fetchMediaUrls(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private MediaPreprocessingService service() {
        return new MediaPreprocessingService(
                youtube, instagram, youtubeFetcher, instagramFetcher,
                accounts, bodyMapper, policies, CLOCK);
    }

    private void stubInstagramAccount() {
        when(accounts.findBySelectorsIdAndDeletedFalse(1L))
                .thenReturn(Optional.of(SelectorsSnsAccount.builder()
                        .selectorsId(1L)
                        .snsCode(SnsPlatform.INSTAGRAM)
                        .accountId("selector.insta")
                        .build()));
    }

    private Content youtubeContent() {
        return content(SnsPlatform.YOUTUBE, "https://youtu.be/abc123");
    }

    private Content instagramContent() {
        Content content = content(SnsPlatform.INSTAGRAM, "https://instagram.com/p/post-id");
        ReflectionTestUtils.setField(content, "snsContentId", "post-1");
        return content;
    }

    private Content content(SnsPlatform platform, String url) {
        Content content = Content.create(1L, platform, url, "LONG_FORM");
        ReflectionTestUtils.setField(content, "id", 10L);
        return content;
    }

    private ContentMedia video(int sequenceNo, Map<String, Object> body) {
        ContentMedia media = ContentMedia.create(
                20L, MediaType.VIDEO, null, null, "abc123", sequenceNo, body);
        ReflectionTestUtils.setField(media, "id", 30L + sequenceNo);
        return media;
    }

    private ContentMedia instagramMedia(
            MediaType type, int sequenceNo, String mediaUrl, String thumbnailUrl) {
        ContentMedia media = ContentMedia.create(
                20L, type, mediaUrl, thumbnailUrl, "media-" + sequenceNo,
                sequenceNo, Map.of());
        ReflectionTestUtils.setField(media, "id", 40L + sequenceNo);
        return media;
    }

    private ContentMediaExtractionResult extraction(String segmentId) {
        return new ContentMediaExtractionResult(
                "1.2",
                new ContentMediaExtractionResult.SttExtraction("ko", List.of(
                        new ContentMediaExtractionResult.SttSegment(
                                segmentId, 0L, 1_000L, "sample"))),
                ContentMediaExtractionResult.OcrExtraction.empty());
    }

    private ContentExtractionExecutionResult execution(
            ContentMediaExtractionResult extraction) {
        return new ContentExtractionExecutionResult(
                extraction, "request-id", "requested", "selected", "response",
                100L, 1, null, null, null, null);
    }

    private InspectionPolicy policy(Long id, String extractionHash) {
        InspectionPolicy policy = mock(InspectionPolicy.class);
        when(policy.getId()).thenReturn(id);
        when(policy.getExtractionConfigHash()).thenReturn(extractionHash);
        return policy;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
