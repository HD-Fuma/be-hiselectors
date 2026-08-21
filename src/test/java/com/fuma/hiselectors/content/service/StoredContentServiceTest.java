package com.fuma.hiselectors.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.client.ContentFetcher;
import com.fuma.hiselectors.content.client.dto.RawContent;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentEngagement;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.repository.ContentEngagementRepository;
import com.fuma.hiselectors.content.repository.ContentMediaRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.service.GenerationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
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
                List.of(instagramFetcher, youtubeFetcher),
                engagementRepository,
                versionRepository,
                mediaRepository,
                snapshotFactory,
                transactionTemplate,
                Clock.fixed(
                        Instant.parse("2026-08-20T03:00:00Z"),
                        ZoneId.of("Asia/Seoul")));
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
        when(instagramFetcher.fetchByContentIds(List.of("instagram-id")))
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
        when(instagramFetcher.fetchByContentIds(List.of("found", "not-found")))
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

        assertThat(result).isEqualTo(new StoredContentService.StoredContentResult(1, 0));
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
        when(instagramFetcher.fetchByContentIds(List.of("changed", "unchanged")))
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

        service.check();

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
        when(instagramFetcher.fetchByContentIds(List.of("not-found", "found", "failed")))
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

        assertThat(result).isEqualTo(new StoredContentService.StoredContentResult(0, 0));
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
        when(instagramFetcher.fetchByContentIds(List.of("duplicate")))
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

        assertThat(result).isEqualTo(new StoredContentService.StoredContentResult(0, 0));
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
        when(instagramFetcher.fetchByContentIds(List.of("first", "second")))
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

        assertThat(result).isEqualTo(new StoredContentService.StoredContentResult(1, 1));
        verify(versionRepository).findCurrentByContentIdIn(List.of(20L));
    }

    @Test
    void propagatesFetcherExceptionBeforeStartingTransactions() {
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        Content content = content(SnsPlatform.INSTAGRAM, "fetch-fails");
        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(contentRepository.findAllByGenerationId(3L)).thenReturn(List.of(content));
        when(instagramFetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(instagramFetcher.fetchByContentIds(List.of("fetch-fails")))
                .thenThrow(new IllegalStateException("fetch failed"));

        assertThatThrownBy(service::check)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("fetch failed");
        verifyNoInteractions(transactionTemplate);
    }

    @Test
    void propagatesMissingFetchResultBeforeStartingTransactions() {
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        Content content = content(SnsPlatform.INSTAGRAM, "missing");
        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(contentRepository.findAllByGenerationId(3L)).thenReturn(List.of(content));
        when(instagramFetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(instagramFetcher.fetchByContentIds(List.of("missing"))).thenReturn(List.of());

        assertThatThrownBy(service::check)
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("missing");
        verifyNoInteractions(transactionTemplate);
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

    private <T> List<T> toList(Iterable<T> values) {
        return java.util.stream.StreamSupport.stream(values.spliterator(), false).toList();
    }
}
