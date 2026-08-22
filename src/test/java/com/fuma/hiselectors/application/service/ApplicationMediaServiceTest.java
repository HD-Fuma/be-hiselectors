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
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.TransactionStatus;
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
    void collectRequestsStatisticsOnlyForLatestTenDistinctContents() {
        Application application = application(SnsPlatform.YOUTUBE, "channel-id");
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));

        LocalDateTime now = COLLECTED_AT;
        List<RawContent> contents = new ArrayList<>();
        for (int i = 0; i < 52; i++) {
            contents.add(raw(SnsPlatform.YOUTUBE, "video-" + i, now.minusHours(i)));
        }
        contents.add(raw(SnsPlatform.YOUTUBE, "video-0", now.minusDays(10)));
        contents.add(raw(SnsPlatform.YOUTUBE, "old", now.minusDays(91)));
        contents.add(raw(SnsPlatform.INSTAGRAM, "wrong-platform", now));
        when(youtubeFetcher.fetchByAccount(any(), any())).thenReturn(contents);
        when(youtubeFetcher.addStatistics(any())).thenAnswer(invocation -> {
            List<RawContent> selected = invocation.getArgument(0);
            assertThat(selected).extracting(RawContent::snsContentId)
                    .containsExactly(
                            "video-0", "video-1", "video-2", "video-3", "video-4",
                            "video-5", "video-6", "video-7", "video-8", "video-9");
            return selected.stream()
                    .map(content -> "video-0".equals(content.snsContentId())
                            ? content.withMetrics(100L, 20L, 3L)
                            : content)
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
            assertThat(media.getViewCount()).isEqualTo(100L);
            assertThat(media.getLikeCount()).isEqualTo(20L);
            assertThat(media.getCommentCount()).isEqualTo(3L);
            assertThat(media.getContentType()).isNull();
        });
        assertThat(saved.get().get(10).getViewCount()).isNull();
        verify(mediaRepository).deleteByApplicationId(APPLICATION_ID);
        verify(mediaRepository).flush();
        assertThat(application.getMediaCollectionStatus()).isEqualTo(MediaCollectionStatus.DONE);
        assertThat(application.getMediaCollectedAt()).isEqualTo(COLLECTED_AT);
        assertThat(saved.get())
                .extracting(ApplicationMedia::getCollectedAt)
                .containsOnly(COLLECTED_AT);
        verify(youtubeFetcher).fetchByAccount(
                "channel-id", COLLECTED_AT.minusDays(90));
    }

    @Test
    void collectRequestsAllInstagramContentsAndSkipsMissingMediaUrls() {
        Application application = application(SnsPlatform.INSTAGRAM, "username");
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        RawContent available = new RawContent(
                SnsPlatform.INSTAGRAM,
                "available",
                "https://www.instagram.com/p/available",
                ContentType.FEED,
                List.of(),
                LocalDateTime.of(2010, 1, 1, 0, 0),
                List.of(new RawContentMedia(
                        "available-media",
                        RawContentMedia.MediaType.IMAGE,
                        "https://cdn.example.com/available.jpg")));
        RawContent unavailable = new RawContent(
                SnsPlatform.INSTAGRAM,
                "unavailable",
                "https://www.instagram.com/p/unavailable",
                ContentType.FEED,
                List.of(),
                LocalDateTime.of(2009, 1, 1, 0, 0),
                List.of(new RawContentMedia(
                        "unavailable-media",
                        RawContentMedia.MediaType.VIDEO,
                        null)));
        when(instagramFetcher.fetchByAccount("username", LocalDateTime.MIN))
                .thenReturn(List.of(available, unavailable));
        when(instagramFetcher.addStatistics(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mediaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.collect(APPLICATION_ID);

        assertThat(result.fetchedCount()).isEqualTo(2);
        assertThat(result.storedCount()).isEqualTo(1);
        assertThat(result.media()).singleElement().satisfies(media -> {
            assertThat(media.snsContentId()).isEqualTo("available");
            assertThat(media.mediaUrls())
                    .containsExactly("https://cdn.example.com/available.jpg");
        });
        verify(instagramFetcher).fetchByAccount("username", LocalDateTime.MIN);
    }

    @Test
    void collectDropsInstagramMetricsOutsideLatestTenSamples() {
        Application application = application(SnsPlatform.INSTAGRAM, "username");
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        List<RawContent> contents = IntStream.range(0, 11)
                .mapToObj(index -> raw(
                        SnsPlatform.INSTAGRAM,
                        "post-" + index,
                        COLLECTED_AT.minusDays(index),
                        1_000L + index,
                        100L + index,
                        10L + index))
                .toList();
        when(instagramFetcher.fetchByAccount(any(), any())).thenReturn(contents);
        when(instagramFetcher.addStatistics(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AtomicReference<List<ApplicationMedia>> saved = new AtomicReference<>();
        when(mediaRepository.saveAll(any())).thenAnswer(invocation -> {
            List<ApplicationMedia> values = invocation.getArgument(0);
            saved.set(values);
            return values;
        });

        service.collect(APPLICATION_ID);

        assertThat(saved.get()).hasSize(11);
        assertThat(saved.get().get(9)).satisfies(media -> {
            assertThat(media.getViewCount()).isEqualTo(1_009L);
            assertThat(media.getLikeCount()).isEqualTo(109L);
            assertThat(media.getCommentCount()).isEqualTo(19L);
        });
        assertThat(saved.get().get(10)).satisfies(media -> {
            assertThat(media.getViewCount()).isNull();
            assertThat(media.getLikeCount()).isNull();
            assertThat(media.getCommentCount()).isNull();
        });
    }

    @Test
    void collectStoresEveryMediaAndThumbnailUrl() {
        Application application = application(SnsPlatform.INSTAGRAM, "username");
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        RawContent content = new RawContent(
                SnsPlatform.INSTAGRAM,
                "post-1",
                "https://www.instagram.com/reel/post-1",
                ContentType.SHORT_FORM,
                List.of(),
                COLLECTED_AT,
                List.of(
                        new RawContentMedia(
                                "media-1",
                                RawContentMedia.MediaType.VIDEO,
                                "https://cdn.example.com/post-1.mp4",
                                List.of("https://cdn.example.com/post-1.jpg")),
                        new RawContentMedia(
                                "media-2",
                                RawContentMedia.MediaType.IMAGE,
                                "https://cdn.example.com/post-2.jpg"),
                        new RawContentMedia(
                                "media-3",
                                RawContentMedia.MediaType.IMAGE,
                                null)));
        when(instagramFetcher.fetchByAccount(any(), any())).thenReturn(List.of(content));
        when(instagramFetcher.addStatistics(any())).thenReturn(List.of(content));
        when(mediaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.collect(APPLICATION_ID);

        assertThat(result.media()).singleElement().satisfies(media -> {
            assertThat(media.contentUrl())
                    .isEqualTo("https://www.instagram.com/reel/post-1");
            assertThat(media.mediaUrl())
                    .isEqualTo("https://cdn.example.com/post-1.mp4");
            assertThat(media.mediaUrls()).containsExactly(
                    "https://cdn.example.com/post-1.mp4",
                    "https://cdn.example.com/post-2.jpg");
            assertThat(media.thumbnailUrls())
                    .containsExactly("https://cdn.example.com/post-1.jpg");
            assertThat(media.contentType()).isEqualTo(ContentType.SHORT_FORM);
        });
    }

    @Test
    void collectKeepsExistingSnapshotWhenClientFails() {
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
    void findLatestReturnsStoredContents() {
        Application application = application(SnsPlatform.INSTAGRAM, "username");
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(mediaRepository.findTop3ByApplicationIdOrderBySequenceNoAsc(APPLICATION_ID))
                .thenReturn(List.of(media("post-1", 0), media("post-2", 1), media("post-3", 2)));

        var result = service.findLatest(APPLICATION_ID);

        assertThat(result)
                .extracting(response -> response.snsContentId())
                .containsExactly("post-1", "post-2", "post-3");
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
        return raw(platform, contentId, createdAt, null, null, null);
    }

    private RawContent raw(SnsPlatform platform, String contentId, LocalDateTime createdAt,
                           Long viewCount, Long likeCount, Long commentCount) {
        List<RawContentMedia> media = platform == SnsPlatform.INSTAGRAM
                ? List.of(new RawContentMedia(
                        contentId, RawContentMedia.MediaType.IMAGE,
                        "https://cdn.example.com/" + contentId + ".jpg"))
                : List.of();
        return new RawContent(
                platform,
                contentId,
                "https://example.com/" + contentId,
                ContentType.FEED,
                List.of(),
                createdAt,
                media,
                viewCount,
                likeCount,
                commentCount);
    }

    private ApplicationMedia media(String contentId, int sequenceNo) {
        return ApplicationMedia.builder()
                .applicationId(APPLICATION_ID)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId(contentId)
                .mediaUrl("https://example.com/" + contentId)
                .sequenceNo(sequenceNo)
                .publishedAt(COLLECTED_AT.minusDays(sequenceNo))
                .collectedAt(COLLECTED_AT)
                .build();
    }
}
