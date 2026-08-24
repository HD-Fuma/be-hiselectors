package com.fuma.hiselectors.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.ApplicationMedia;
import com.fuma.hiselectors.application.model.ApplicationStatus;
import com.fuma.hiselectors.application.model.MediaCollectionStatus;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.repository.ApplicationMediaRepository;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.content.client.ContentFetcher;
import com.fuma.hiselectors.content.client.dto.RawContent;
import com.fuma.hiselectors.content.client.dto.RawContentMedia;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.content.model.MediaType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class ApplicationMediaServiceTest {

    private static final Long APPLICATION_ID = 1L;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-20T03:00:00Z"), SEOUL);
    private static final LocalDateTime COLLECTED_AT = LocalDateTime.now(CLOCK);

    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private ApplicationMediaRepository mediaRepository;
    @Mock
    private ContentFetcher instagramFetcher;
    @Mock
    private ContentFetcher youtubeFetcher;
    @Mock
    private TransactionTemplate transactionTemplate;

    private ApplicationMediaService service;

    @BeforeEach
    void setUp() {
        lenient().when(instagramFetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        lenient().when(youtubeFetcher.supports()).thenReturn(SnsPlatform.YOUTUBE);
        lenient().when(instagramFetcher.fetchProfileImageUrl(any()))
                .thenReturn(Optional.empty());
        lenient().when(youtubeFetcher.fetchProfileImageUrl(any()))
                .thenReturn(Optional.empty());
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        lenient().doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        service = new ApplicationMediaService(
                applicationRepository, mediaRepository,
                List.of(instagramFetcher, youtubeFetcher), transactionTemplate, CLOCK);
    }

    @Test
    void collectsStatisticsForEveryRecentDistinctPostAndPersistsWeightedRate() {
        Application application = application(SnsPlatform.YOUTUBE, "channel-id");
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));

        List<RawContent> contents = new ArrayList<>();
        for (int index = 0; index < 52; index++) {
            contents.add(raw(
                    SnsPlatform.YOUTUBE, "video-" + index, COLLECTED_AT.minusHours(index)));
        }
        contents.add(raw(SnsPlatform.YOUTUBE, "video-0", COLLECTED_AT.minusDays(10)));
        contents.add(raw(SnsPlatform.YOUTUBE, "old", COLLECTED_AT.minusDays(91)));
        contents.add(raw(SnsPlatform.INSTAGRAM, "wrong-platform", COLLECTED_AT));
        when(youtubeFetcher.fetchByAccount(any(), any())).thenReturn(contents);
        when(youtubeFetcher.fetchProfileImageUrl("channel-id"))
                .thenReturn(Optional.of("https://cdn.example.com/channel-profile.jpg"));
        when(youtubeFetcher.addStatistics(any())).thenAnswer(invocation -> {
            List<RawContent> selected = invocation.getArgument(0);
            assertThat(selected)
                    .extracting(RawContent::snsContentId)
                    .containsExactlyElementsOf(IntStream.range(0, 52)
                            .mapToObj(index -> "video-" + index)
                            .toList());
            return selected.stream()
                    .map(content -> switch (content.snsContentId()) {
                        case "video-0" -> content.withMetrics(100L, 20L, 3L);
                        case "video-1" -> content.withMetrics(200L, 10L, 1L);
                        default -> content;
                    })
                    .toList();
        });

        AtomicReference<List<ApplicationMedia>> saved = new AtomicReference<>();
        when(mediaRepository.saveAll(any())).thenAnswer(invocation -> {
            List<ApplicationMedia> values = invocation.getArgument(0);
            saved.set(values);
            return values;
        });

        var result = service.collect(APPLICATION_ID);

        assertThat(result.fetchedCount()).isEqualTo(contents.size());
        assertThat(result.storedCount()).isEqualTo(52);
        assertThat(saved.get())
                .extracting(ApplicationMedia::getSnsContentId)
                .containsExactlyElementsOf(IntStream.range(0, 52)
                        .mapToObj(index -> "video-" + index)
                        .toList());
        assertThat(saved.get())
                .extracting(ApplicationMedia::getSequenceNo)
                .containsExactlyElementsOf(IntStream.range(0, 52).boxed().toList());
        assertThat(saved.get().getFirst()).satisfies(media -> {
            assertThat(media.getSnsMediaId()).isEqualTo("video-0-media");
            assertThat(media.getMediaSequenceNo()).isZero();
            assertThat(media.getMediaType()).isEqualTo(MediaType.VIDEO);
            assertThat(media.getMediaUrl()).isNull();
            assertThat(media.getThumbnailUrl())
                    .isEqualTo("https://cdn.example.com/video-0.jpg");
            assertThat(media.getTitle()).isEqualTo("title video-0");
            assertThat(media.getDescription()).isEqualTo("description video-0");
            assertThat(media.getContentType()).isEqualTo(ContentType.LONG_FORM);
            assertThat(media.getViewCount()).isEqualTo(100L);
        });
        verify(mediaRepository).deleteByApplicationId(APPLICATION_ID);
        verify(mediaRepository).flush();
        verify(youtubeFetcher).fetchByAccount("channel-id", COLLECTED_AT.minusDays(90));
        assertThat(application.getMediaCollectionStatus()).isEqualTo(MediaCollectionStatus.DONE);
        assertThat(application.getMediaCollectedAt()).isEqualTo(COLLECTED_AT);
        assertThat(application.getEngagementRate()).isEqualByComparingTo("15.00");
        assertThat(application.getProfileImageUrl())
                .isEqualTo("https://cdn.example.com/channel-profile.jpg");
        assertThat(saved.get())
                .extracting(ApplicationMedia::getCollectedAt)
                .containsOnly(COLLECTED_AT);
    }

    @Test
    void storesOneRowPerInstagramAssetAndDropsOnlyInvalidChildren() {
        Application application = application(SnsPlatform.INSTAGRAM, "username");
        application.updateProfileImageUrl("https://cdn.example.com/existing-profile.jpg");
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        RawContent reel = new RawContent(
                SnsPlatform.INSTAGRAM,
                "reel-1",
                "https://www.instagram.com/reel/reel-1",
                ContentType.SHORT_FORM,
                List.of("reel caption"),
                COLLECTED_AT,
                List.of(
                        new RawContentMedia(
                                "media-1", RawContentMedia.MediaType.VIDEO,
                                "https://cdn.example.com/reel.mp4",
                                List.of("https://cdn.example.com/reel.jpg")),
                        new RawContentMedia(
                                "media-2", RawContentMedia.MediaType.IMAGE,
                                "https://cdn.example.com/image.jpg"),
                        new RawContentMedia(
                                "missing-url", RawContentMedia.MediaType.IMAGE, null),
                        new RawContentMedia(
                                "text", RawContentMedia.MediaType.TEXT,
                                "https://cdn.example.com/text")));
        RawContent post = new RawContent(
                SnsPlatform.INSTAGRAM,
                "post-1",
                "https://www.instagram.com/p/post-1",
                ContentType.FEED,
                List.of("post caption"),
                COLLECTED_AT.minusDays(1),
                List.of(new RawContentMedia(
                        "media-3", RawContentMedia.MediaType.IMAGE,
                        "https://cdn.example.com/post.jpg")));
        RawContent unavailable = new RawContent(
                SnsPlatform.INSTAGRAM,
                "unavailable",
                "https://www.instagram.com/p/unavailable",
                ContentType.FEED,
                List.of("unavailable"),
                COLLECTED_AT.minusDays(2),
                List.of(new RawContentMedia(
                        "unavailable-media", RawContentMedia.MediaType.VIDEO, null)));
        when(instagramFetcher.fetchByAccount("username", LocalDateTime.MIN))
                .thenReturn(List.of(reel, post, unavailable));
        when(instagramFetcher.fetchProfileImageUrl("username"))
                .thenThrow(new IllegalStateException("profile API failed"));
        when(instagramFetcher.addStatistics(any())).thenAnswer(invocation -> {
            List<RawContent> selected = invocation.getArgument(0);
            assertThat(selected).extracting(RawContent::snsContentId)
                    .containsExactly("reel-1", "post-1");
            return selected;
        });
        when(mediaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.collect(APPLICATION_ID);

        assertThat(result.fetchedCount()).isEqualTo(3);
        assertThat(result.storedCount()).isEqualTo(3);
        assertThat(result.media())
                .extracting(media -> media.snsContentId() + ":" + media.snsMediaId())
                .containsExactly("reel-1:media-1", "reel-1:media-2", "post-1:media-3");
        assertThat(result.media().getFirst()).satisfies(media -> {
            assertThat(media.sequenceNo()).isZero();
            assertThat(media.mediaSequenceNo()).isZero();
            assertThat(media.contentType()).isEqualTo(ContentType.SHORT_FORM);
            assertThat(media.mediaType()).isEqualTo(MediaType.VIDEO);
            assertThat(media.caption()).isEqualTo("reel caption");
            assertThat(media.mediaUrl()).isEqualTo("https://cdn.example.com/reel.mp4");
            assertThat(media.thumbnailUrl()).isEqualTo("https://cdn.example.com/reel.jpg");
        });
        assertThat(result.media().get(1)).satisfies(media -> {
            assertThat(media.sequenceNo()).isZero();
            assertThat(media.mediaSequenceNo()).isEqualTo(1);
            assertThat(media.mediaType()).isEqualTo(MediaType.IMAGE);
            assertThat(media.thumbnailUrl()).isNull();
        });
        assertThat(result.media().get(2)).satisfies(media -> {
            assertThat(media.sequenceNo()).isEqualTo(1);
            assertThat(media.mediaSequenceNo()).isZero();
            assertThat(media.contentType()).isEqualTo(ContentType.FEED);
            assertThat(media.caption()).isEqualTo("post caption");
        });
        assertThat(application.getMediaCollectionStatus()).isEqualTo(MediaCollectionStatus.DONE);
        assertThat(application.getProfileImageUrl())
                .isEqualTo("https://cdn.example.com/existing-profile.jpg");
    }

    @Test
    void ignoresBlankAndOverlongProfileImageUrls() {
        Application application = application(SnsPlatform.YOUTUBE, "channel-id");
        application.updateProfileImageUrl("https://cdn.example.com/existing-profile.jpg");

        application.updateProfileImageUrl("   ");
        application.updateProfileImageUrl("x".repeat(501));

        assertThat(application.getProfileImageUrl())
                .isEqualTo("https://cdn.example.com/existing-profile.jpg");
    }

    @Test
    void keepsExistingSnapshotWhenClientFails() {
        Application application = application(SnsPlatform.YOUTUBE, "channel-id");
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(youtubeFetcher.fetchByAccount(any(), any()))
                .thenThrow(new IllegalStateException("API failed"));

        assertThatThrownBy(() -> service.collect(APPLICATION_ID))
                .isInstanceOf(IllegalStateException.class);

        verify(mediaRepository, never()).deleteByApplicationId(any());
        verify(mediaRepository, never()).saveAll(any());
        assertThat(application.getMediaCollectionStatus()).isEqualTo(MediaCollectionStatus.FAILED);
        assertThat(application.getMediaCollectionRetryCount()).isEqualTo(1);
        assertThat(application.getMediaCollectionError()).isEqualTo("API failed");
    }

    @Test
    void findLatestReturnsStoredAssetsInPostAndAssetOrder() {
        Application application = application(SnsPlatform.INSTAGRAM, "username");
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(mediaRepository.findTop3ByApplicationIdOrderBySequenceNoAscMediaSequenceNoAsc(
                APPLICATION_ID))
                .thenReturn(List.of(
                        media("post-1", "media-1", 0, 0),
                        media("post-1", "media-2", 0, 1),
                        media("post-2", "media-3", 1, 0)));

        var result = service.findLatest(APPLICATION_ID);

        assertThat(result)
                .extracting(response -> response.snsContentId() + ":" + response.snsMediaId())
                .containsExactly("post-1:media-1", "post-1:media-2", "post-2:media-3");
    }

    private Application application(SnsPlatform platform, String accountId) {
        Application application = Application.builder()
                .userId(10L)
                .generationId(20L)
                .snsCode(platform)
                .snsAccountId(accountId)
                .alarmYn(true)
                .policyAgreedAt(COLLECTED_AT.minusDays(30))
                .status(ApplicationStatus.PENDING)
                .build();
        ReflectionTestUtils.setField(application, "id", APPLICATION_ID);
        return application;
    }

    private RawContent raw(SnsPlatform platform, String contentId, LocalDateTime createdAt) {
        List<RawContentMedia> media = platform == SnsPlatform.INSTAGRAM
                ? List.of(new RawContentMedia(
                        contentId + "-media", RawContentMedia.MediaType.IMAGE,
                        "https://cdn.example.com/" + contentId + ".jpg"))
                : List.of(new RawContentMedia(
                        contentId + "-media", RawContentMedia.MediaType.VIDEO, null,
                        List.of("https://cdn.example.com/" + contentId + ".jpg")));
        List<String> texts = platform == SnsPlatform.YOUTUBE
                ? List.of("title " + contentId, "description " + contentId)
                : List.of("caption " + contentId);
        return new RawContent(
                platform,
                contentId,
                "https://example.com/" + contentId,
                platform == SnsPlatform.YOUTUBE ? ContentType.LONG_FORM : ContentType.FEED,
                texts,
                createdAt,
                media);
    }

    private ApplicationMedia media(
            String contentId, String mediaId, int sequenceNo, int mediaSequenceNo) {
        return ApplicationMedia.builder()
                .applicationId(APPLICATION_ID)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId(contentId)
                .snsMediaId(mediaId)
                .mediaUrl("https://example.com/" + mediaId)
                .contentType(ContentType.POST)
                .mediaType(MediaType.IMAGE)
                .sequenceNo(sequenceNo)
                .mediaSequenceNo(mediaSequenceNo)
                .publishedAt(COLLECTED_AT.minusDays(sequenceNo))
                .collectedAt(COLLECTED_AT)
                .build();
    }
}
