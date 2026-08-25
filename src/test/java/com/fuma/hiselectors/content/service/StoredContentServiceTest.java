package com.fuma.hiselectors.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.client.ContentFetcher;
import com.fuma.hiselectors.content.client.dto.RawContent;
import com.fuma.hiselectors.content.client.dto.RawContentMedia;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentEngagement;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.repository.ContentEngagementRepository;
import com.fuma.hiselectors.content.repository.ContentBatchAccountRepository;
import com.fuma.hiselectors.content.repository.ContentMediaRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.service.GenerationService;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class StoredContentServiceTest {

    @Mock
    private GenerationService generationService;

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private ContentBatchAccountRepository accountRepository;

    @Mock
    private ContentEngagementRepository engagementRepository;

    @Mock
    private ContentVersionRepository versionRepository;

    @Mock
    private ContentMediaRepository mediaRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private ContentFetcher instagramFetcher;

    @Mock
    private ContentFetcher youtubeFetcher;

    private final ContentSnapshotFactory snapshotFactory = new ContentSnapshotFactory();
    private StoredContentService service;

    @BeforeEach
    void setUp() {
        service = new StoredContentService(
                generationService,
                contentRepository,
                accountRepository,
                List.of(instagramFetcher, youtubeFetcher),
                engagementRepository,
                versionRepository,
                mediaRepository,
                snapshotFactory,
                transactionTemplate,
                Clock.fixed(
                        Instant.parse("2026-08-20T03:00:00Z"),
                        ZoneId.of("Asia/Seoul")));
        lenient().when(accountRepository.findAllByGenerationId(anyLong()))
                .thenReturn(List.of(SelectorsSnsAccount.builder()
                        .selectorsId(1L)
                        .snsCode(SnsPlatform.INSTAGRAM)
                        .accountId("selector.insta")
                        .build()));
    }

    @Test
    void fetchesCurrentGenerationContentsByPlatform() {
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        Content instagram = content(SnsPlatform.INSTAGRAM, "instagram-id");
        Content youtubeFound = content(SnsPlatform.YOUTUBE, "youtube-found");
        Content youtubeMissing = content(SnsPlatform.YOUTUBE, "youtube-missing");
        ContentFetcher.Engagement engagement =
                new ContentFetcher.Engagement(null, 20L, 3L, null);
        ContentFetcher.FetchResult instagramResult = new ContentFetcher.FetchResult(
                "instagram-id",
                ContentFetcher.FetchStatus.FOUND,
                org.mockito.Mockito.mock(RawContent.class),
                engagement);
        ContentFetcher.FetchResult youtubeFoundResult = new ContentFetcher.FetchResult(
                "youtube-found",
                ContentFetcher.FetchStatus.FOUND,
                org.mockito.Mockito.mock(RawContent.class),
                engagement);
        ContentFetcher.FetchResult youtubeMissingResult = new ContentFetcher.FetchResult(
                "youtube-missing",
                ContentFetcher.FetchStatus.NOT_FOUND,
                null,
                null);

        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(contentRepository.findAllByGenerationId(3L))
                .thenReturn(List.of(instagram, youtubeFound, youtubeMissing));
        when(instagramFetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(youtubeFetcher.supports()).thenReturn(SnsPlatform.YOUTUBE);
        when(instagramFetcher.fetchByAccountContentIds(
                "selector.insta", List.of("instagram-id")))
                .thenReturn(List.of(instagramResult));
        when(youtubeFetcher.fetchByContentIds(
                List.of("youtube-found", "youtube-missing")))
                .thenReturn(List.of(youtubeFoundResult, youtubeMissingResult));

        List<StoredContentService.StoredContentFetch> result = service.fetchStoredContents();

        assertThat(result).containsExactly(
                new StoredContentService.StoredContentFetch(instagram, instagramResult),
                new StoredContentService.StoredContentFetch(youtubeFound, youtubeFoundResult),
                new StoredContentService.StoredContentFetch(youtubeMissing, youtubeMissingResult));
    }

    @Test
    void fetchesInstagramContentsBySelectorsAccount() {
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        Content first = content(SnsPlatform.INSTAGRAM, "first-media");
        Content second = content(SnsPlatform.INSTAGRAM, "second-media");
        ReflectionTestUtils.setField(second, "selectorsId", 2L);
        ContentFetcher.FetchResult firstResult = new ContentFetcher.FetchResult(
                "first-media", ContentFetcher.FetchStatus.FOUND,
                org.mockito.Mockito.mock(RawContent.class), null);
        ContentFetcher.FetchResult secondResult = new ContentFetcher.FetchResult(
                "second-media", ContentFetcher.FetchStatus.FOUND,
                org.mockito.Mockito.mock(RawContent.class), null);

        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(contentRepository.findAllByGenerationId(3L))
                .thenReturn(List.of(first, second));
        when(accountRepository.findAllByGenerationId(3L)).thenReturn(List.of(
                SelectorsSnsAccount.builder()
                        .selectorsId(1L)
                        .snsCode(SnsPlatform.INSTAGRAM)
                        .accountId("first.selector")
                        .build(),
                SelectorsSnsAccount.builder()
                        .selectorsId(2L)
                        .snsCode(SnsPlatform.INSTAGRAM)
                        .accountId("second.selector")
                        .build()));
        when(instagramFetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(instagramFetcher.fetchByAccountContentIds(
                "first.selector", List.of("first-media")))
                .thenReturn(List.of(firstResult));
        when(instagramFetcher.fetchByAccountContentIds(
                "second.selector", List.of("second-media")))
                .thenReturn(List.of(secondResult));

        List<StoredContentService.StoredContentFetch> result = service.fetchStoredContents();

        assertThat(result).containsExactly(
                new StoredContentService.StoredContentFetch(first, firstResult),
                new StoredContentService.StoredContentFetch(second, secondResult));
        verify(instagramFetcher, never()).fetchByContentIds(any());
    }

    @Test
    void savesEngagementForFoundContent() {
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        Content found = content(SnsPlatform.INSTAGRAM, "found");
        Content notFound = content(SnsPlatform.INSTAGRAM, "not-found");
        ReflectionTestUtils.setField(found, "id", 10L);
        ReflectionTestUtils.setField(notFound, "id", 20L);
        ContentFetcher.Engagement engagement =
                new ContentFetcher.Engagement(100L, 20L, 3L, 4L);
        RawContent rawContent = raw("found", "같은 본문");
        ContentFetcher.FetchResult foundResult = new ContentFetcher.FetchResult(
                "found",
                ContentFetcher.FetchStatus.FOUND,
                rawContent,
                engagement);
        ContentFetcher.FetchResult notFoundResult = new ContentFetcher.FetchResult(
                "not-found",
                ContentFetcher.FetchStatus.NOT_FOUND,
                null,
                null);
        AtomicReference<List<ContentEngagement>> saved = new AtomicReference<>();

        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(contentRepository.findAllByGenerationId(3L))
                .thenReturn(List.of(found, notFound));
        when(instagramFetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(instagramFetcher.fetchByAccountContentIds(
                "selector.insta", List.of("found", "not-found")))
                .thenReturn(List.of(foundResult, notFoundResult));
        when(versionRepository.findCurrentByContentIdIn(List.of(10L)))
                .thenReturn(List.of(ContentVersion.builder()
                        .contentId(10L)
                        .versionNo(1L)
                        .contentHash(snapshotFactory.contentHash(rawContent))
                        .createdAt(LocalDateTime.of(2026, 8, 19, 12, 0))
                        .build()));
        executeTransaction();
        when(engagementRepository.saveAll(any())).thenAnswer(invocation -> {
            List<ContentEngagement> values = toList(invocation.getArgument(0));
            saved.set(values);
            return values;
        });

        StoredContentService.StoredContentResult result = service.check();

        assertThat(result.savedEngagementCount()).isEqualTo(1);
        assertThat(result.failedContentCount()).isZero();
        assertThat(result.platformStats()).containsEntry(
                SnsPlatform.INSTAGRAM,
                new StoredContentService.PlatformStoredContentStats(0, 0));
        assertThat(saved.get()).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.getContentId()).isEqualTo(10L);
            assertThat(snapshot.getViewCount()).isEqualTo(100L);
            assertThat(snapshot.getLikeCount()).isEqualTo(20L);
            assertThat(snapshot.getCommentCount()).isEqualTo(3L);
            assertThat(snapshot.getShareCount()).isEqualTo(4L);
            assertThat(snapshot.getCreatedAt())
                    .isEqualTo(LocalDateTime.of(2026, 8, 20, 12, 0));
        });
    }

    @Test
    void savesNewVersionOnlyForModifiedContent() {
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        Content changed = content(SnsPlatform.INSTAGRAM, "changed");
        Content unchanged = content(SnsPlatform.INSTAGRAM, "unchanged");
        ReflectionTestUtils.setField(changed, "id", 10L);
        ReflectionTestUtils.setField(unchanged, "id", 20L);
        RawContent changedBefore = raw("changed", "수정 전");
        RawContent changedNow = raw("changed", "수정 후");
        RawContent unchangedNow = raw("unchanged", "동일 본문");
        ContentFetcher.FetchResult changedResult = new ContentFetcher.FetchResult(
                "changed", ContentFetcher.FetchStatus.FOUND, changedNow, null);
        ContentFetcher.FetchResult unchangedResult = new ContentFetcher.FetchResult(
                "unchanged", ContentFetcher.FetchStatus.FOUND, unchangedNow, null);
        AtomicReference<List<ContentVersion>> savedVersions = new AtomicReference<>();
        AtomicReference<List<ContentMedia>> savedMedia = new AtomicReference<>();

        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(contentRepository.findAllByGenerationId(3L))
                .thenReturn(List.of(changed, unchanged));
        when(instagramFetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(instagramFetcher.fetchByAccountContentIds(
                "selector.insta", List.of("changed", "unchanged")))
                .thenReturn(List.of(changedResult, unchangedResult));
        when(versionRepository.findCurrentByContentIdIn(List.of(10L)))
                .thenReturn(List.of(version(
                        10L, snapshotFactory.contentHash(changedBefore))));
        when(versionRepository.findCurrentByContentIdIn(List.of(20L)))
                .thenReturn(List.of(version(
                        20L, snapshotFactory.contentHash(unchangedNow))));
        executeTransaction();
        when(contentRepository.saveAll(any())).thenAnswer(invocation ->
                toList(invocation.getArgument(0)));
        when(versionRepository.saveAll(any())).thenAnswer(invocation -> {
            List<ContentVersion> values = toList(invocation.getArgument(0));
            ReflectionTestUtils.setField(values.getFirst(), "id", 100L);
            savedVersions.set(values);
            return values;
        });
        when(mediaRepository.saveAll(any())).thenAnswer(invocation -> {
            List<ContentMedia> values = toList(invocation.getArgument(0));
            savedMedia.set(values);
            return values;
        });

        StoredContentService.StoredContentResult result = service.check();

        assertThat(result.platformStats()).containsEntry(
                SnsPlatform.INSTAGRAM,
                new StoredContentService.PlatformStoredContentStats(1, 0));
        assertThat(changed.getLastVersionNo()).isEqualTo(2L);
        assertThat(unchanged.getLastVersionNo()).isEqualTo(1L);
        assertThat(savedVersions.get()).singleElement().satisfies(version -> {
            assertThat(version.getContentId()).isEqualTo(10L);
            assertThat(version.getVersionNo()).isEqualTo(2L);
            assertThat(version.getContentHash())
                    .isEqualTo(snapshotFactory.contentHash(changedNow));
        });
        assertThat(savedMedia.get()).singleElement().satisfies(media -> {
            assertThat(media.getContentVersionId()).isEqualTo(100L);
            assertThat(media.getBody()).containsExactlyEntriesOf(Map.of("text", "수정 후"));
        });
        verify(contentRepository).saveAll(List.of(changed));
    }

    @Test
    void refreshesExternalMediaWithoutCreatingNewVersion() {
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        Content content = content(SnsPlatform.INSTAGRAM, "same");
        ReflectionTestUtils.setField(content, "id", 10L);
        RawContent fetched = new RawContent(
                SnsPlatform.INSTAGRAM,
                "same",
                "https://example.com/same",
                ContentType.SHORT_FORM,
                "동일 본문",
                LocalDateTime.of(2026, 8, 20, 11, 0),
                List.of(new RawContentMedia(
                        "video-1",
                        RawContentMedia.MediaType.VIDEO,
                        "https://cdn.example.com/video-new.mp4",
                        List.of("https://cdn.example.com/video-new.jpg"))));
        ContentVersion current = version(10L, snapshotFactory.contentHash(fetched));
        ReflectionTestUtils.setField(current, "id", 100L);
        ContentMedia stored = ContentMedia.create(
                100L,
                com.fuma.hiselectors.content.model.MediaType.VIDEO,
                "https://cdn.example.com/video-old.mp4",
                null,
                "video-1",
                1,
                Map.of());

        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(contentRepository.findAllByGenerationId(3L)).thenReturn(List.of(content));
        when(instagramFetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(instagramFetcher.fetchByAccountContentIds(
                "selector.insta", List.of("same")))
                .thenReturn(List.of(new ContentFetcher.FetchResult(
                        "same", ContentFetcher.FetchStatus.FOUND, fetched, null)));
        when(versionRepository.findCurrentByContentIdIn(List.of(10L)))
                .thenReturn(List.of(current));
        when(mediaRepository.findByContentVersionIdOrderBySequenceNoAsc(100L))
                .thenReturn(List.of(stored));
        executeTransaction();

        StoredContentService.StoredContentResult result = service.check();

        assertThat(result.failedContentCount()).isZero();
        assertThat(result.platformStats()).containsEntry(
                SnsPlatform.INSTAGRAM,
                new StoredContentService.PlatformStoredContentStats(0, 0));
        assertThat(stored.getMediaUrl())
                .isEqualTo("https://cdn.example.com/video-new.mp4");
        assertThat(stored.getThumbnailUrl())
                .isEqualTo("https://cdn.example.com/video-new.jpg");
        verify(mediaRepository).saveAll(List.of(stored));
        verify(versionRepository, never()).saveAll(any());
    }

    @Test
    void refreshesMediaOnlyResultAndRestoresContent() {
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        Content content = content(SnsPlatform.INSTAGRAM, "older-video");
        ReflectionTestUtils.setField(content, "id", 10L);
        content.markDeleted();
        ContentVersion current = version(10L, "existing-hash");
        ReflectionTestUtils.setField(current, "id", 100L);
        ContentMedia stored = ContentMedia.create(
                100L,
                com.fuma.hiselectors.content.model.MediaType.VIDEO,
                "https://cdn.example.com/video-old.mp4",
                null,
                "older-video",
                1,
                Map.of());
        RawContentMedia refreshed = new RawContentMedia(
                "older-video",
                RawContentMedia.MediaType.VIDEO,
                "https://cdn.example.com/video-new.mp4",
                List.of("https://cdn.example.com/video-new.jpg"));

        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(contentRepository.findAllByGenerationId(3L)).thenReturn(List.of(content));
        when(instagramFetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(instagramFetcher.fetchByAccountContentIds(
                "selector.insta", List.of("older-video")))
                .thenReturn(List.of(new ContentFetcher.FetchResult(
                        "older-video",
                        ContentFetcher.FetchStatus.FOUND,
                        null,
                        null,
                        List.of(refreshed))));
        when(versionRepository.findCurrentByContentIdIn(List.of(10L)))
                .thenReturn(List.of(current));
        when(mediaRepository.findByContentVersionIdOrderBySequenceNoAsc(100L))
                .thenReturn(List.of(stored));
        executeTransaction();

        StoredContentService.StoredContentResult result = service.check();

        assertThat(result.failedContentCount()).isZero();
        assertThat(content.isDeleted()).isFalse();
        assertThat(content.getLastVersionNo()).isEqualTo(1L);
        assertThat(stored.getMediaUrl())
                .isEqualTo("https://cdn.example.com/video-new.mp4");
        assertThat(stored.getThumbnailUrl())
                .isEqualTo("https://cdn.example.com/video-new.jpg");
        verify(mediaRepository).saveAll(List.of(stored));
        verify(contentRepository).saveAll(List.of(content));
        verify(versionRepository, never()).saveAll(any());
    }

    @Test
    void updatesDeletionOnlyForFoundAndNotFoundContent() {
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        Content notFound = content(SnsPlatform.INSTAGRAM, "not-found");
        Content found = content(SnsPlatform.INSTAGRAM, "found");
        Content failed = content(SnsPlatform.INSTAGRAM, "failed");
        ReflectionTestUtils.setField(notFound, "id", 10L);
        ReflectionTestUtils.setField(found, "id", 20L);
        ReflectionTestUtils.setField(failed, "id", 30L);
        found.markDeleted();
        failed.markDeleted();
        RawContent foundRaw = raw("found", "동일 본문");

        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(contentRepository.findAllByGenerationId(3L))
                .thenReturn(List.of(notFound, found, failed));
        when(instagramFetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(instagramFetcher.fetchByAccountContentIds(
                "selector.insta", List.of("not-found", "found", "failed")))
                .thenReturn(List.of(
                        new ContentFetcher.FetchResult(
                                "not-found", ContentFetcher.FetchStatus.NOT_FOUND, null, null),
                        new ContentFetcher.FetchResult(
                                "found", ContentFetcher.FetchStatus.FOUND, foundRaw, null),
                        new ContentFetcher.FetchResult(
                                "failed", ContentFetcher.FetchStatus.FAILED, null, null)));
        when(versionRepository.findCurrentByContentIdIn(List.of(20L)))
                .thenReturn(List.of(version(
                        20L, snapshotFactory.contentHash(foundRaw))));
        executeTransaction();
        when(contentRepository.saveAll(any())).thenAnswer(invocation ->
                toList(invocation.getArgument(0)));

        StoredContentService.StoredContentResult result = service.check();

        assertThat(result.savedEngagementCount()).isZero();
        assertThat(result.failedContentCount()).isEqualTo(1);
        assertThat(result.platformStats()).containsEntry(
                SnsPlatform.INSTAGRAM,
                new StoredContentService.PlatformStoredContentStats(0, 1));
        assertThat(notFound.isDeleted()).isTrue();
        assertThat(found.isDeleted()).isFalse();
        assertThat(failed.isDeleted()).isTrue();
        verify(contentRepository).saveAll(List.of(notFound));
        verify(contentRepository).saveAll(List.of(found));
    }

    @Test
    void skipsDuplicateEngagementButStillSavesChangedVersionAndRestoresContent() {
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        Content content = content(SnsPlatform.INSTAGRAM, "duplicate");
        ReflectionTestUtils.setField(content, "id", 10L);
        content.markDeleted();
        RawContent before = raw("duplicate", "수정 전");
        RawContent now = raw("duplicate", "수정 후");

        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(contentRepository.findAllByGenerationId(3L)).thenReturn(List.of(content));
        when(instagramFetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(instagramFetcher.fetchByAccountContentIds(
                "selector.insta", List.of("duplicate")))
                .thenReturn(List.of(new ContentFetcher.FetchResult(
                        "duplicate",
                        ContentFetcher.FetchStatus.FOUND,
                        now,
                        new ContentFetcher.Engagement(100L, 20L, 3L, 4L))));
        when(engagementRepository.existsByContentIdAndCreatedAt(
                10L, LocalDateTime.of(2026, 8, 20, 12, 0)))
                .thenReturn(true);
        when(versionRepository.findCurrentByContentIdIn(List.of(10L)))
                .thenReturn(List.of(version(10L, snapshotFactory.contentHash(before))));
        executeTransaction();
        when(contentRepository.saveAll(any())).thenAnswer(invocation ->
                toList(invocation.getArgument(0)));
        when(versionRepository.saveAll(any())).thenAnswer(invocation -> {
            List<ContentVersion> values = toList(invocation.getArgument(0));
            ReflectionTestUtils.setField(values.getFirst(), "id", 100L);
            return values;
        });

        StoredContentService.StoredContentResult result = service.check();

        assertThat(result.savedEngagementCount()).isZero();
        assertThat(result.failedContentCount()).isZero();
        assertThat(result.platformStats()).containsEntry(
                SnsPlatform.INSTAGRAM,
                new StoredContentService.PlatformStoredContentStats(1, 0));
        assertThat(content.getLastVersionNo()).isEqualTo(2L);
        assertThat(content.isDeleted()).isFalse();
        verify(engagementRepository, never()).saveAll(any());
        verify(versionRepository).saveAll(any());
        verify(mediaRepository).saveAll(any());
        verify(contentRepository).saveAll(List.of(content));
    }

    @Test
    void continuesAfterFirstContentPersistenceFails() {
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        Content first = content(SnsPlatform.INSTAGRAM, "first");
        Content second = content(SnsPlatform.INSTAGRAM, "second");
        ReflectionTestUtils.setField(first, "id", 10L);
        ReflectionTestUtils.setField(second, "id", 20L);
        RawContent firstRaw = raw("first", "본문 1");
        RawContent secondRaw = raw("second", "본문 2");

        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(contentRepository.findAllByGenerationId(3L)).thenReturn(List.of(first, second));
        when(instagramFetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(instagramFetcher.fetchByAccountContentIds(
                "selector.insta", List.of("first", "second")))
                .thenReturn(List.of(
                        new ContentFetcher.FetchResult(
                                "first", ContentFetcher.FetchStatus.FOUND, firstRaw,
                                new ContentFetcher.Engagement(100L, 20L, 3L, 4L)),
                        new ContentFetcher.FetchResult(
                                "second", ContentFetcher.FetchStatus.FOUND, secondRaw,
                                new ContentFetcher.Engagement(200L, 30L, 4L, 5L))));
        when(engagementRepository.existsByContentIdAndCreatedAt(any(), any()))
                .thenReturn(false);
        when(engagementRepository.saveAll(any()))
                .thenThrow(new IllegalStateException("first failed"))
                .thenAnswer(invocation -> toList(invocation.getArgument(0)));
        when(versionRepository.findCurrentByContentIdIn(List.of(20L)))
                .thenReturn(List.of(version(20L, snapshotFactory.contentHash(secondRaw))));
        executeTransaction();

        StoredContentService.StoredContentResult result = service.check();

        assertThat(result.savedEngagementCount()).isEqualTo(1);
        assertThat(result.failedContentCount()).isEqualTo(1);
        assertThat(result.platformStats()).containsEntry(
                SnsPlatform.INSTAGRAM,
                new StoredContentService.PlatformStoredContentStats(0, 1));
        verify(versionRepository).findCurrentByContentIdIn(List.of(20L));
    }

    @Test
    void continuesWithYoutubeWhenInstagramFetcherFails() {
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        Content instagram = content(SnsPlatform.INSTAGRAM, "fetch-fails");
        Content youtube = content(SnsPlatform.YOUTUBE, "youtube-found");
        ContentFetcher.FetchResult youtubeResult = new ContentFetcher.FetchResult(
                "youtube-found",
                ContentFetcher.FetchStatus.FOUND,
                org.mockito.Mockito.mock(RawContent.class),
                null);
        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(contentRepository.findAllByGenerationId(3L))
                .thenReturn(List.of(instagram, youtube));
        when(instagramFetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(youtubeFetcher.supports()).thenReturn(SnsPlatform.YOUTUBE);
        when(instagramFetcher.fetchByAccountContentIds(
                "selector.insta", List.of("fetch-fails")))
                .thenThrow(new IllegalStateException("fetch failed"));
        when(youtubeFetcher.fetchByContentIds(List.of("youtube-found")))
                .thenReturn(List.of(youtubeResult));

        List<StoredContentService.StoredContentFetch> result = service.fetchStoredContents();

        assertThat(result).containsExactly(
                new StoredContentService.StoredContentFetch(
                        instagram,
                        new ContentFetcher.FetchResult(
                                "fetch-fails", ContentFetcher.FetchStatus.FAILED, null, null)),
                new StoredContentService.StoredContentFetch(youtube, youtubeResult));
        verify(youtubeFetcher).fetchByContentIds(List.of("youtube-found"));
    }

    @Test
    void separatesInstagramAndYoutubeFailures() {
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        Content instagram = content(SnsPlatform.INSTAGRAM, "instagram-missing");
        Content youtube = content(SnsPlatform.YOUTUBE, "youtube-missing");

        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(contentRepository.findAllByGenerationId(3L))
                .thenReturn(List.of(instagram, youtube));
        when(instagramFetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(youtubeFetcher.supports()).thenReturn(SnsPlatform.YOUTUBE);
        when(instagramFetcher.fetchByAccountContentIds(
                "selector.insta", List.of("instagram-missing"))).thenReturn(List.of());
        when(youtubeFetcher.fetchByContentIds(List.of("youtube-missing")))
                .thenReturn(List.of());

        StoredContentService.StoredContentResult result = service.check();

        assertThat(result.failedContentCount()).isEqualTo(2);
        assertThat(result.platformStats()).containsExactlyInAnyOrderEntriesOf(Map.of(
                SnsPlatform.INSTAGRAM,
                new StoredContentService.PlatformStoredContentStats(0, 1),
                SnsPlatform.YOUTUBE,
                new StoredContentService.PlatformStoredContentStats(0, 1)));
    }

    @Test
    void countsMissingFetchResultAsFailureWithoutStartingTransaction() {
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        Content content = content(SnsPlatform.INSTAGRAM, "missing");
        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(contentRepository.findAllByGenerationId(3L)).thenReturn(List.of(content));
        when(instagramFetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(instagramFetcher.fetchByAccountContentIds(
                "selector.insta", List.of("missing"))).thenReturn(List.of());

        StoredContentService.StoredContentResult result = service.check();

        assertThat(result.savedEngagementCount()).isZero();
        assertThat(result.failedContentCount()).isEqualTo(1);
        assertThat(result.platformStats()).containsEntry(
                SnsPlatform.INSTAGRAM,
                new StoredContentService.PlatformStoredContentStats(0, 1));
    }

    @Test
    void reportsEveryCheckedContentIncludingContentsWithoutSavedEngagement() {
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        Content first = content(SnsPlatform.INSTAGRAM, "first");
        Content second = content(SnsPlatform.INSTAGRAM, "second");
        List<StoredContentService.StoredContentProgress> progress = new ArrayList<>();
        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(contentRepository.findAllByGenerationId(3L)).thenReturn(List.of(first, second));
        when(instagramFetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(instagramFetcher.fetchByAccountContentIds(
                "selector.insta", List.of("first", "second"))).thenReturn(List.of());

        StoredContentService.StoredContentResult result = service.check(progress::add);

        assertThat(progress).containsExactly(
                new StoredContentService.StoredContentProgress(2, 0, 0),
                new StoredContentService.StoredContentProgress(2, 1, 1),
                new StoredContentService.StoredContentProgress(2, 2, 2));
        assertThat(result.checkedContentCount()).isEqualTo(2);
        assertThat(result.savedEngagementCount()).isZero();
    }

    @Test
    void reportsZeroTargetSnapshotWithoutExternalFetch() {
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        List<StoredContentService.StoredContentProgress> progress = new ArrayList<>();
        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(contentRepository.findAllByGenerationId(3L)).thenReturn(List.of());

        StoredContentService.StoredContentResult result = service.check(progress::add);

        assertThat(progress).containsExactly(
                new StoredContentService.StoredContentProgress(0, 0, 0));
        assertThat(result.checkedContentCount()).isZero();
        verifyNoInteractions(instagramFetcher, youtubeFetcher);
    }

    @Test
    void reportsInitialTotalBeforeExternalFetchAndCumulativeProgressAfterEachContent() {
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        Content found = content(SnsPlatform.INSTAGRAM, "found");
        Content failed = content(SnsPlatform.INSTAGRAM, "failed");
        ReflectionTestUtils.setField(found, "id", 10L);
        List<StoredContentService.StoredContentProgress> progress = new ArrayList<>();
        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(contentRepository.findAllByGenerationId(3L)).thenReturn(List.of(found, failed));
        when(instagramFetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(instagramFetcher.fetchByAccountContentIds(
                "selector.insta", List.of("found", "failed"))).thenAnswer(invocation -> {
                    assertThat(progress).containsExactly(
                            new StoredContentService.StoredContentProgress(2, 0, 0));
                    return List.of(
                            new ContentFetcher.FetchResult(
                                    "found", ContentFetcher.FetchStatus.FOUND,
                                    raw("found", "같은 본문"), null),
                            new ContentFetcher.FetchResult(
                                    "failed", ContentFetcher.FetchStatus.FAILED, null, null));
                });
        when(versionRepository.findCurrentByContentIdIn(List.of(10L)))
                .thenReturn(List.of(version(
                        10L, snapshotFactory.contentHash(raw("found", "같은 본문")))));
        executeTransaction();

        service.check(progress::add);

        assertThat(progress).containsExactly(
                new StoredContentService.StoredContentProgress(2, 0, 0),
                new StoredContentService.StoredContentProgress(2, 1, 0),
                new StoredContentService.StoredContentProgress(2, 2, 1));
        InOrder order = inOrder(contentRepository, instagramFetcher);
        order.verify(contentRepository).findAllByGenerationId(3L);
        order.verify(instagramFetcher).fetchByAccountContentIds(
                "selector.insta", List.of("found", "failed"));
    }

    @Test
    void reportsEveryProcessedSnapshotAfterItsDomainTransactionCompletes() {
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        Content first = content(SnsPlatform.INSTAGRAM, "first");
        Content second = content(SnsPlatform.INSTAGRAM, "second");
        AtomicBoolean inTransaction = new AtomicBoolean();
        AtomicBoolean transactionCompleted = new AtomicBoolean();
        List<StoredContentService.StoredContentProgress> progress = new ArrayList<>();
        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(contentRepository.findAllByGenerationId(3L)).thenReturn(List.of(first, second));
        when(instagramFetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(instagramFetcher.fetchByAccountContentIds(
                "selector.insta", List.of("first", "second"))).thenReturn(List.of(
                        new ContentFetcher.FetchResult(
                                "first", ContentFetcher.FetchStatus.NOT_FOUND, null, null),
                        new ContentFetcher.FetchResult(
                                "second", ContentFetcher.FetchStatus.NOT_FOUND, null, null)));
        executeTransaction(inTransaction, transactionCompleted);

        service.check(update -> {
            if (update.checkedContentCount() > 0) {
                assertThat(inTransaction).isFalse();
                assertThat(transactionCompleted).isTrue();
                transactionCompleted.set(false);
            }
            progress.add(update);
        });

        assertThat(progress).containsExactly(
                new StoredContentService.StoredContentProgress(2, 0, 0),
                new StoredContentService.StoredContentProgress(2, 1, 0),
                new StoredContentService.StoredContentProgress(2, 2, 0));
    }

    @Test
    void propagatesProcessedProgressFailureAfterCleanDomainTransactionCompletes() {
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        Content content = content(SnsPlatform.INSTAGRAM, "not-found");
        IllegalStateException failure = new IllegalStateException("progress failed");
        AtomicBoolean inTransaction = new AtomicBoolean();
        AtomicBoolean transactionCompleted = new AtomicBoolean();
        AtomicBoolean callbackObservedCompletion = new AtomicBoolean();
        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(contentRepository.findAllByGenerationId(3L)).thenReturn(List.of(content));
        when(instagramFetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(instagramFetcher.fetchByAccountContentIds(
                "selector.insta", List.of("not-found"))).thenReturn(List.of(
                        new ContentFetcher.FetchResult(
                                "not-found", ContentFetcher.FetchStatus.NOT_FOUND, null, null)));
        executeTransaction(inTransaction, transactionCompleted);

        assertThatThrownBy(() -> service.check(update -> {
            if (update.checkedContentCount() > 0) {
                callbackObservedCompletion.set(
                        !inTransaction.get() && transactionCompleted.get());
                throw failure;
            }
        })).isSameAs(failure);

        assertThat(callbackObservedCompletion).isTrue();
        assertThat(inTransaction).isFalse();
    }

    @Test
    void propagatesProgressCallbackFailure() {
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        Content content = content(SnsPlatform.INSTAGRAM, "missing");
        IllegalStateException failure = new IllegalStateException("progress failed");
        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(contentRepository.findAllByGenerationId(3L)).thenReturn(List.of(content));

        assertThatThrownBy(() -> service.check(update -> {
            throw failure;
        })).isSameAs(failure);

        verifyNoInteractions(instagramFetcher, youtubeFetcher);
    }

    @Test
    void rejectsNullProgressCallbackBeforeFetchingContents() {
        assertThatThrownBy(() -> service.check(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("진행 콜백은 필수입니다.");

        verifyNoInteractions(generationService);
    }

    private Content content(SnsPlatform platform, String snsContentId) {
        return Content.builder()
                .selectorsId(1L)
                .snsCode(platform)
                .snsContentId(snsContentId)
                .contentUrl("https://example.com/" + snsContentId)
                .contentType(ContentType.FEED)
                .build();
    }

    private RawContent raw(String snsContentId, String text) {
        return new RawContent(
                SnsPlatform.INSTAGRAM,
                snsContentId,
                "https://example.com/" + snsContentId,
                ContentType.FEED,
                text,
                LocalDateTime.of(2026, 8, 20, 11, 0),
                List.of());
    }

    private ContentVersion version(Long contentId, String hash) {
        return ContentVersion.builder()
                .contentId(contentId)
                .versionNo(1L)
                .contentHash(hash)
                .createdAt(LocalDateTime.of(2026, 8, 19, 12, 0))
                .build();
    }

    private void executeTransaction() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    private void executeTransaction(
            AtomicBoolean inTransaction, AtomicBoolean transactionCompleted) {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            inTransaction.set(true);
            try {
                return callback.doInTransaction(null);
            } finally {
                inTransaction.set(false);
                transactionCompleted.set(true);
            }
        });
    }

    private <T> List<T> toList(Iterable<T> values) {
        return java.util.stream.StreamSupport.stream(values.spliterator(), false).toList();
    }
}
