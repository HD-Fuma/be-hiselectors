package com.fuma.hiselectors.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.classifier.SelectorsContentClassifier;
import com.fuma.hiselectors.content.client.ContentFetcher;
import com.fuma.hiselectors.content.client.dto.RawContent;
import com.fuma.hiselectors.content.client.dto.RawContentMedia;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.content.repository.ContentBatchAccountRepository;
import com.fuma.hiselectors.content.repository.ContentMediaRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class NewContentServiceTest {

    @Mock
    private GenerationService generationService;

    @Mock
    private ContentBatchAccountRepository accountRepository;

    @Mock
    private ContentFetcher fetcher;

    @Mock
    private ContentFetcher youtubeFetcher;

    @Mock
    private SelectorsContentClassifier classifier;

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private ContentVersionRepository versionRepository;

    @Mock
    private ContentMediaRepository mediaRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    private NewContentService service;

    @BeforeEach
    void setUp() {
        service = new NewContentService(
                generationService,
                accountRepository,
                List.of(fetcher, youtubeFetcher),
                classifier,
                contentRepository,
                versionRepository,
                mediaRepository,
                new ContentSnapshotFactory(),
                transactionTemplate,
                Clock.fixed(
                        Instant.parse("2026-08-20T03:00:00Z"),
                        ZoneId.of("Asia/Seoul")));
        lenient().when(accountRepository.advanceCollectionCursorIfAccountUnchanged(
                any(), any(), any(), any())).thenReturn(1);
    }

    @Test
    void createsCollectionTargetsForCurrentGeneration() {
        LocalDateTime generationStart = LocalDateTime.of(2026, 8, 1, 0, 0);
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        when(generation.getId()).thenReturn(3L);
        when(generation.getActivityStartDate()).thenReturn(generationStart);

        SelectorsSnsAccount neverCollected = account(null);
        SelectorsSnsAccount collectedBeforeGeneration =
                account(generationStart.minusDays(1));
        SelectorsSnsAccount collectedDuringGeneration =
                account(generationStart.plusHours(2));
        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(accountRepository.findAllByGenerationId(3L)).thenReturn(List.of(
                neverCollected, collectedBeforeGeneration, collectedDuringGeneration));

        List<NewContentService.CollectionTarget> targets = service.collectionTargets();

        assertThat(targets).extracting(NewContentService.CollectionTarget::account)
                .containsExactly(
                        neverCollected, collectedBeforeGeneration, collectedDuringGeneration);
        assertThat(targets).extracting(NewContentService.CollectionTarget::since)
                .containsExactly(
                        generationStart, generationStart, generationStart.plusHours(2));
        verify(accountRepository).findAllByGenerationId(3L);
    }

    @Test
    void fastModeCreatesTargetsOnlyForConfiguredAccounts() {
        LocalDateTime generationStart = LocalDateTime.of(2026, 8, 1, 0, 0);
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        SelectorsSnsAccount youtube = platformAccount(
                SnsPlatform.YOUTUBE, "UCD2RQE52TloxzZxZ2fyq8HQ", null);
        SelectorsSnsAccount instagram = platformAccount(
                SnsPlatform.INSTAGRAM, "@HI_SELECTORS", null);
        SelectorsSnsAccount unrelated = platformAccount(
                SnsPlatform.INSTAGRAM, "another_handle", null);
        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(generation.getActivityStartDate()).thenReturn(generationStart);
        when(accountRepository.findAllByGenerationId(3L))
                .thenReturn(List.of(youtube, unrelated, instagram));

        assertThat(service.collectionTargets(ContentBatchMode.FAST))
                .extracting(NewContentService.CollectionTarget::account)
                .containsExactly(youtube, instagram);
    }

    @Test
    void selectsOnlyNewSelectorsContentCandidates() {
        LocalDateTime since = LocalDateTime.of(2026, 8, 20, 10, 0);
        SelectorsSnsAccount account = SelectorsSnsAccount.builder()
                .selectorsId(1L)
                .snsCode(SnsPlatform.INSTAGRAM)
                .accountId("instagram-account")
                .build();
        NewContentService.CollectionTarget target =
                new NewContentService.CollectionTarget(account, since);
        RawContent existing = raw("existing", "셀렉터스 콘텐츠");
        RawContent duplicate = raw("duplicate", "셀렉터스 콘텐츠");
        RawContent rejected = raw("rejected", "일반 콘텐츠");
        RawContent selected = raw("selected", "더현대 셀렉터스");

        when(fetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(fetcher.fetchByAccount("instagram-account", since))
                .thenReturn(List.of(existing, duplicate, duplicate, rejected, selected));
        when(contentRepository.findAllBySnsCodeAndSnsContentIdIn(
                SnsPlatform.INSTAGRAM,
                List.of("existing", "duplicate", "rejected", "selected")))
                .thenReturn(List.of(Content.builder()
                        .selectorsId(1L)
                        .snsCode(SnsPlatform.INSTAGRAM)
                        .snsContentId("existing")
                        .contentUrl("https://example.com/existing")
                        .contentType(ContentType.FEED)
                        .build()));
        when(classifier.isSelectorsContent(duplicate)).thenReturn(true);
        when(classifier.isSelectorsContent(rejected)).thenReturn(false);
        when(classifier.isSelectorsContent(selected)).thenReturn(true);

        NewContentService.NewContentSelection selection = service.newCandidates(target);

        assertThat(selection.candidateCount()).isEqualTo(3);
        assertThat(selection.selectorsContents()).containsExactly(duplicate, selected);
        verify(fetcher).fetchByAccount("instagram-account", since);
        verify(fetcher).addStatistics(List.of(duplicate, rejected, selected));
    }

    @Test
    void classifiesYoutubeShortsWithAddStatisticsBeforeSave() {
        LocalDateTime since = LocalDateTime.of(2026, 8, 20, 10, 0);
        SelectorsSnsAccount account = SelectorsSnsAccount.builder()
                .selectorsId(1L)
                .snsCode(SnsPlatform.YOUTUBE)
                .accountId("youtube-account")
                .build();
        NewContentService.CollectionTarget target =
                new NewContentService.CollectionTarget(account, since);
        RawContent playlistItem = new RawContent(
                SnsPlatform.YOUTUBE,
                "short-1",
                "https://www.youtube.com/watch?v=short-1",
                ContentType.LONG_FORM,
                List.of("셀렉터스 콘텐츠"),
                LocalDateTime.of(2026, 8, 20, 11, 0),
                List.of());
        RawContent classified = playlistItem.withContentType(ContentType.SHORTS);

        when(youtubeFetcher.supports()).thenReturn(SnsPlatform.YOUTUBE);
        when(youtubeFetcher.fetchByAccount("youtube-account", since))
                .thenReturn(List.of(playlistItem));
        when(contentRepository.findAllBySnsCodeAndSnsContentIdIn(
                SnsPlatform.YOUTUBE, List.of("short-1")))
                .thenReturn(List.of());
        when(youtubeFetcher.addStatistics(List.of(playlistItem)))
                .thenReturn(List.of(classified));
        when(classifier.isSelectorsContent(classified)).thenReturn(true);

        NewContentService.NewContentSelection selection = service.newCandidates(target);

        assertThat(selection.selectorsContents()).containsExactly(classified);
        assertThat(selection.selectorsContents().getFirst().contentType())
                .isEqualTo(ContentType.SHORTS);
    }

    @Test
    void savesNewContentVersionMediaAndCollectionCursor() {
        LocalDateTime generationStart = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime collectedAt = LocalDateTime.of(2026, 8, 20, 12, 0);
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        SelectorsSnsAccount account = instagramAccount(null);
        RawContent selected = new RawContent(
                SnsPlatform.INSTAGRAM,
                "selected",
                "https://example.com/selected",
                ContentType.FEED,
                List.of("첫 번째 본문"),
                LocalDateTime.of(2026, 8, 20, 11, 0),
                List.of(new RawContentMedia(
                        "image-1",
                        RawContentMedia.MediaType.IMAGE,
                        "https://cdn.example.com/image.jpg")));
        AtomicReference<List<Content>> savedContents = new AtomicReference<>();
        AtomicReference<List<ContentVersion>> savedVersions = new AtomicReference<>();
        AtomicReference<List<ContentMedia>> savedMedia = new AtomicReference<>();

        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(generation.getActivityStartDate()).thenReturn(generationStart);
        when(accountRepository.findAllByGenerationId(3L)).thenReturn(List.of(account));
        when(fetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(fetcher.fetchByAccount("instagram-account", generationStart))
                .thenReturn(List.of(selected));
        when(contentRepository.findAllBySnsCodeAndSnsContentIdIn(
                SnsPlatform.INSTAGRAM, List.of("selected")))
                .thenReturn(List.of());
        when(classifier.isSelectorsContent(selected)).thenReturn(true);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(contentRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Content> values = toList(invocation.getArgument(0));
            ReflectionTestUtils.setField(values.getFirst(), "id", 100L);
            savedContents.set(values);
            return values;
        });
        when(versionRepository.saveAll(any())).thenAnswer(invocation -> {
            List<ContentVersion> values = toList(invocation.getArgument(0));
            ReflectionTestUtils.setField(values.getFirst(), "id", 200L);
            savedVersions.set(values);
            return values;
        });
        when(mediaRepository.saveAll(any())).thenAnswer(invocation -> {
            List<ContentMedia> values = toList(invocation.getArgument(0));
            savedMedia.set(values);
            return values;
        });

        NewContentService.NewContentResult result = service.collect();

        assertThat(result.savedContentCount()).isEqualTo(1);
        assertThat(result.failedAccountCount()).isZero();
        assertThat(result.platformStats()).containsEntry(
                SnsPlatform.INSTAGRAM,
                new NewContentService.PlatformCollectionStats(1, 1, 1, 0));
        assertThat(savedContents.get()).singleElement().satisfies(content -> {
            assertThat(content.getSelectorsId()).isEqualTo(1L);
            assertThat(content.getSnsContentId()).isEqualTo("selected");
            assertThat(content.getLastVersionNo()).isEqualTo(1L);
        });
        assertThat(savedVersions.get()).singleElement().satisfies(version -> {
            assertThat(version.getContentId()).isEqualTo(100L);
            assertThat(version.getVersionNo()).isEqualTo(1L);
            assertThat(version.getContentHash()).hasSize(64);
            assertThat(version.getCreatedAt()).isEqualTo(collectedAt);
        });
        assertThat(savedMedia.get()).hasSize(2);
        assertThat(savedMedia.get().getFirst()).satisfies(media -> {
            assertThat(media.getContentVersionId()).isEqualTo(200L);
            assertThat(media.getMediaType()).isEqualTo(MediaType.TEXT);
            assertThat(media.getSequenceNo()).isZero();
            assertThat(media.getBody()).containsExactlyEntriesOf(
                    Map.of("text", "첫 번째 본문"));
        });
        assertThat(savedMedia.get().get(1)).satisfies(media -> {
            assertThat(media.getMediaType()).isEqualTo(MediaType.IMAGE);
            assertThat(media.getSnsMediaId()).isEqualTo("image-1");
            assertThat(media.getSequenceNo()).isEqualTo(1);
        });
        verify(accountRepository).advanceCollectionCursorIfAccountUnchanged(
                account.getId(), SnsPlatform.INSTAGRAM, "instagram-account", collectedAt);
    }

    @Test
    void separatesInstagramAndYoutubeCollectionStats() {
        LocalDateTime generationStart = LocalDateTime.of(2026, 8, 1, 0, 0);
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        SelectorsSnsAccount instagram = platformAccount(
                SnsPlatform.INSTAGRAM, "instagram-account", null);
        SelectorsSnsAccount youtube = platformAccount(
                SnsPlatform.YOUTUBE, "youtube-account", null);
        RawContent instagramContent = raw(SnsPlatform.INSTAGRAM, "instagram-content");
        RawContent youtubeContent = raw(SnsPlatform.YOUTUBE, "youtube-content");

        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(generation.getActivityStartDate()).thenReturn(generationStart);
        when(accountRepository.findAllByGenerationId(3L)).thenReturn(List.of(instagram, youtube));
        when(fetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(youtubeFetcher.supports()).thenReturn(SnsPlatform.YOUTUBE);
        when(fetcher.fetchByAccount("instagram-account", generationStart))
                .thenReturn(List.of(instagramContent));
        when(youtubeFetcher.fetchByAccount("youtube-account", generationStart))
                .thenReturn(List.of(youtubeContent));
        when(contentRepository.findAllBySnsCodeAndSnsContentIdIn(
                SnsPlatform.INSTAGRAM, List.of("instagram-content"))).thenReturn(List.of());
        when(contentRepository.findAllBySnsCodeAndSnsContentIdIn(
                SnsPlatform.YOUTUBE, List.of("youtube-content"))).thenReturn(List.of());
        when(classifier.isSelectorsContent(instagramContent)).thenReturn(true);
        when(classifier.isSelectorsContent(youtubeContent)).thenReturn(true);
        when(transactionTemplate.execute(any())).thenReturn(List.of(101L));

        NewContentService.NewContentResult result = service.collect();

        assertThat(result.platformStats()).containsExactlyInAnyOrderEntriesOf(Map.of(
                SnsPlatform.INSTAGRAM,
                new NewContentService.PlatformCollectionStats(1, 1, 1, 0),
                SnsPlatform.YOUTUBE,
                new NewContentService.PlatformCollectionStats(1, 1, 1, 0)));
    }

    @Test
    void countsFailedAccountWhenTransactionSaveFails() {
        LocalDateTime generationStart = LocalDateTime.of(2026, 8, 1, 0, 0);
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        SelectorsSnsAccount account = instagramAccount(null);
        RawContent selected = raw("selected", "더현대 셀렉터스");
        List<NewContentService.NewContentProgress> progress = new ArrayList<>();

        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(generation.getActivityStartDate()).thenReturn(generationStart);
        when(accountRepository.findAllByGenerationId(3L)).thenReturn(List.of(account));
        when(fetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(fetcher.fetchByAccount("instagram-account", generationStart))
                .thenReturn(List.of(selected));
        when(contentRepository.findAllBySnsCodeAndSnsContentIdIn(
                SnsPlatform.INSTAGRAM, List.of("selected")))
                .thenReturn(List.of());
        when(classifier.isSelectorsContent(selected)).thenReturn(true);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(contentRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Content> values = toList(invocation.getArgument(0));
            ReflectionTestUtils.setField(values.getFirst(), "id", 100L);
            return values;
        });
        when(versionRepository.saveAll(any())).thenAnswer(invocation -> {
            List<ContentVersion> values = toList(invocation.getArgument(0));
            ReflectionTestUtils.setField(values.getFirst(), "id", 200L);
            return values;
        });
        when(mediaRepository.saveAll(any()))
                .thenThrow(new IllegalStateException("media save failed"));

        NewContentService.NewContentResult result = service.collect(progress::add);

        assertThat(progress.stream()
                .map(update -> new NewContentService.NewContentProgress(
                        update.savedContentDelta(), update.failedAccountDelta()))
                .toList()).containsExactly(
                new NewContentService.NewContentProgress(0, 1));
        assertThat(result.savedContentCount()).isZero();
        assertThat(result.failedAccountCount()).isEqualTo(1);
        assertThat(result.platformStats()).containsEntry(
                SnsPlatform.INSTAGRAM,
                new NewContentService.PlatformCollectionStats(1, 1, 0, 1));
        verify(accountRepository).advanceCollectionCursorIfAccountUnchanged(
                account.getId(), SnsPlatform.INSTAGRAM, "instagram-account",
                LocalDateTime.of(2026, 8, 20, 12, 0));
    }

    @Test
    void preservesSuccessfulSavedCountWhenAnotherAccountFetchFails() {
        LocalDateTime generationStart = LocalDateTime.of(2026, 8, 1, 0, 0);
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        SelectorsSnsAccount successfulAccount = instagramAccount("successful-account", null);
        SelectorsSnsAccount failedAccount = instagramAccount("failed-account", null);
        RawContent selected = raw("selected", "더현대 셀렉터스");

        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(generation.getActivityStartDate()).thenReturn(generationStart);
        when(accountRepository.findAllByGenerationId(3L))
                .thenReturn(List.of(successfulAccount, failedAccount));
        when(fetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(fetcher.fetchByAccount("successful-account", generationStart))
                .thenReturn(List.of(selected));
        when(fetcher.fetchByAccount("failed-account", generationStart))
                .thenThrow(new IllegalStateException("fetch failed"));
        when(contentRepository.findAllBySnsCodeAndSnsContentIdIn(
                SnsPlatform.INSTAGRAM, List.of("selected")))
                .thenReturn(List.of());
        when(classifier.isSelectorsContent(selected)).thenReturn(true);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(contentRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Content> values = toList(invocation.getArgument(0));
            ReflectionTestUtils.setField(values.getFirst(), "id", 100L);
            return values;
        });
        when(versionRepository.saveAll(any())).thenAnswer(invocation -> {
            List<ContentVersion> values = toList(invocation.getArgument(0));
            ReflectionTestUtils.setField(values.getFirst(), "id", 200L);
            return values;
        });

        NewContentService.NewContentResult result = service.collect();

        assertThat(result.savedContentCount()).isEqualTo(1);
        assertThat(result.failedAccountCount()).isEqualTo(1);
        assertThat(result.platformStats()).containsEntry(
                SnsPlatform.INSTAGRAM,
                new NewContentService.PlatformCollectionStats(1, 1, 1, 1));
        verify(accountRepository).advanceCollectionCursorIfAccountUnchanged(
                successfulAccount.getId(), SnsPlatform.INSTAGRAM, "successful-account",
                LocalDateTime.of(2026, 8, 20, 12, 0));
        verify(accountRepository, never()).advanceCollectionCursorIfAccountUnchanged(
                failedAccount.getId(), SnsPlatform.INSTAGRAM, "failed-account",
                LocalDateTime.of(2026, 8, 20, 12, 0));
    }

    @Test
    void rejectsFetchedContentWhenAccountChangedBeforeSave() {
        LocalDateTime generationStart = LocalDateTime.of(2026, 8, 1, 0, 0);
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        SelectorsSnsAccount account = instagramAccount(null);
        RawContent selected = raw("selected", "더현대 셀렉터스");

        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(generation.getActivityStartDate()).thenReturn(generationStart);
        when(accountRepository.findAllByGenerationId(3L)).thenReturn(List.of(account));
        when(fetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(fetcher.fetchByAccount("instagram-account", generationStart))
                .thenReturn(List.of(selected));
        when(contentRepository.findAllBySnsCodeAndSnsContentIdIn(
                SnsPlatform.INSTAGRAM, List.of("selected"))).thenReturn(List.of());
        when(classifier.isSelectorsContent(selected)).thenReturn(true);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(accountRepository.advanceCollectionCursorIfAccountUnchanged(
                any(), any(), any(), any())).thenReturn(0);

        NewContentService.NewContentResult result = service.collect();

        assertThat(result.savedContentCount()).isZero();
        assertThat(result.failedAccountCount()).isEqualTo(1);
        assertThat(result.platformStats()).containsEntry(
                SnsPlatform.INSTAGRAM,
                new NewContentService.PlatformCollectionStats(1, 1, 0, 1));
        verify(contentRepository, never()).saveAll(any());
    }

    @Test
    void propagatesGenerationTargetFailure() {
        IllegalStateException failure = new IllegalStateException("generation target failed");
        when(generationService.getCurrentActivity()).thenThrow(failure);

        assertThatThrownBy(service::collect).isSameAs(failure);
    }

    @Test
    void propagatesAccountTargetLookupFailure() {
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        IllegalStateException failure = new IllegalStateException("account target lookup failed");
        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(accountRepository.findAllByGenerationId(3L)).thenThrow(failure);

        assertThatThrownBy(service::collect).isSameAs(failure);
    }

    @Test
    void reportsEachCommittedSavedContentThenTheFailedAccount() {
        LocalDateTime generationStart = LocalDateTime.of(2026, 8, 1, 0, 0);
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        SelectorsSnsAccount successfulAccount = instagramAccount("successful", null);
        SelectorsSnsAccount failedAccount = instagramAccount("failed", null);
        RawContent first = raw("first", "셀렉터스");
        RawContent second = raw("second", "셀렉터스");
        List<NewContentService.NewContentProgress> progress = new ArrayList<>();
        AtomicBoolean transactionReturned = new AtomicBoolean();

        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(generation.getActivityStartDate()).thenReturn(generationStart);
        when(accountRepository.findAllByGenerationId(3L))
                .thenReturn(List.of(successfulAccount, failedAccount));
        when(fetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(fetcher.fetchByAccount("successful", generationStart))
                .thenReturn(List.of(first, second));
        when(fetcher.fetchByAccount("failed", generationStart))
                .thenThrow(new IllegalStateException("fetch failed"));
        when(contentRepository.findAllBySnsCodeAndSnsContentIdIn(
                SnsPlatform.INSTAGRAM, List.of("first", "second"))).thenReturn(List.of());
        when(classifier.isSelectorsContent(first)).thenReturn(true);
        when(classifier.isSelectorsContent(second)).thenReturn(true);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            transactionReturned.set(true);
            return List.of(101L, 102L);
        });

        NewContentService.NewContentResult result = service.collect(update -> {
            assertThat(transactionReturned).isTrue();
            progress.add(update);
        });

        assertThat(progress.stream()
                .map(update -> new NewContentService.NewContentProgress(
                        update.savedContentDelta(), update.failedAccountDelta()))
                .toList()).containsExactly(
                new NewContentService.NewContentProgress(1, 0),
                new NewContentService.NewContentProgress(1, 0),
                new NewContentService.NewContentProgress(0, 1));
        assertThat(result.savedContentCount()).isEqualTo(2);
        assertThat(result.failedAccountCount()).isEqualTo(1);
    }

    @Test
    void propagatesProgressCallbackFailure() {
        LocalDateTime generationStart = LocalDateTime.of(2026, 8, 1, 0, 0);
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        SelectorsSnsAccount account = instagramAccount(null);
        IllegalStateException failure = new IllegalStateException("progress failed");
        List<NewContentService.NewContentProgress> progress = new ArrayList<>();

        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(generation.getActivityStartDate()).thenReturn(generationStart);
        when(accountRepository.findAllByGenerationId(3L)).thenReturn(List.of(account));
        when(fetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        RawContent selected = raw("selected", "셀렉터스");
        when(fetcher.fetchByAccount("instagram-account", generationStart))
                .thenReturn(List.of(selected));
        when(contentRepository.findAllBySnsCodeAndSnsContentIdIn(
                SnsPlatform.INSTAGRAM, List.of("selected"))).thenReturn(List.of());
        when(classifier.isSelectorsContent(selected)).thenReturn(true);
        when(transactionTemplate.execute(any())).thenReturn(List.of(101L));

        assertThatThrownBy(() -> service.collect(update -> {
            progress.add(update);
            throw failure;
        })).isSameAs(failure);
        assertThat(progress).containsExactly(
                new NewContentService.NewContentProgress(1, 0));
    }

    @Test
    void zeroSavedContentsEmitNoSuccessProgress() {
        LocalDateTime generationStart = LocalDateTime.of(2026, 8, 1, 0, 0);
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        List<NewContentService.NewContentProgress> progress = new ArrayList<>();

        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(generation.getActivityStartDate()).thenReturn(generationStart);
        when(accountRepository.findAllByGenerationId(3L))
                .thenReturn(List.of(instagramAccount(null)));
        when(fetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(fetcher.fetchByAccount("instagram-account", generationStart)).thenReturn(List.of());
        when(transactionTemplate.execute(any())).thenReturn(List.of());

        NewContentService.NewContentResult result = service.collect(progress::add);

        assertThat(progress).isEmpty();
        assertThat(result.savedContentCount()).isZero();
        assertThat(result.failedAccountCount()).isZero();
    }

    @Test
    void rejectsNullProgressCallbackBeforeLookingUpTargets() {
        assertThatThrownBy(() -> service.collect(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("진행 콜백은 필수입니다.");

        verifyNoInteractions(generationService);
    }

    @Test
    void reportsSafeBusinessFailureWithStagePlatformAndNormalizedAccountId() {
        LocalDateTime generationStart = LocalDateTime.of(2026, 8, 1, 0, 0);
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        String accountId = "account\n" + "x".repeat(120);
        List<NewContentService.NewContentProgress> progress = new ArrayList<>();
        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(generation.getActivityStartDate()).thenReturn(generationStart);
        when(accountRepository.findAllByGenerationId(3L))
                .thenReturn(List.of(instagramAccount(accountId, null)));
        when(fetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(fetcher.fetchByAccount(accountId, generationStart)).thenThrow(
                new BusinessException(ErrorCode.INSTAGRAM_API_CALL_FAILED, "token=secret"));

        service.collect(progress::add);

        ContentSyncFailure failure = progress.getFirst().failure();
        assertThat(progress.getFirst().failedAccountDelta()).isEqualTo(1);
        assertThat(failure.stage()).isEqualTo("NEW_CONTENT_SYNC");
        assertThat(failure.platform()).isEqualTo(SnsPlatform.INSTAGRAM);
        assertThat(failure.itemType()).isEqualTo("accountId");
        assertThat(failure.itemId()).doesNotContain("\n", "\r").hasSizeLessThanOrEqualTo(80);
        assertThat(failure.errorType()).isEqualTo("INSTAGRAM_API_CALL_FAILED");
        assertThat(failure.errorMessage())
                .isEqualTo(ErrorCode.INSTAGRAM_API_CALL_FAILED.getMessage())
                .doesNotContain("secret");
    }

    @Test
    void preservesTwoArgumentProgressConstructor() {
        assertThat(new NewContentService.NewContentProgress(2, 1))
                .isEqualTo(new NewContentService.NewContentProgress(2, 1, null));
    }

    private SelectorsSnsAccount account(LocalDateTime lastCollectedAt) {
        return SelectorsSnsAccount.builder()
                .selectorsId(1L)
                .accountId("account")
                .lastCollectedAt(lastCollectedAt)
                .build();
    }

    private SelectorsSnsAccount instagramAccount(LocalDateTime lastCollectedAt) {
        return instagramAccount("instagram-account", lastCollectedAt);
    }

    private SelectorsSnsAccount instagramAccount(
            String accountId, LocalDateTime lastCollectedAt) {
        return platformAccount(SnsPlatform.INSTAGRAM, accountId, lastCollectedAt);
    }

    private SelectorsSnsAccount platformAccount(
            SnsPlatform platform, String accountId, LocalDateTime lastCollectedAt) {
        SelectorsSnsAccount account = SelectorsSnsAccount.builder()
                .selectorsId(1L)
                .snsCode(platform)
                .accountId(accountId)
                .lastCollectedAt(lastCollectedAt)
                .build();
        ReflectionTestUtils.setField(account, "id", (long) accountId.hashCode() & 0xffffffffL);
        return account;
    }

    private RawContent raw(String id, String caption) {
        return new RawContent(
                SnsPlatform.INSTAGRAM,
                id,
                "https://example.com/" + id,
                ContentType.FEED,
                caption,
                LocalDateTime.of(2026, 8, 20, 11, 0),
                List.of());
    }

    private RawContent raw(SnsPlatform platform, String id) {
        return new RawContent(
                platform,
                id,
                "https://example.com/" + id,
                ContentType.FEED,
                "셀렉터스 콘텐츠",
                LocalDateTime.of(2026, 8, 20, 11, 0),
                List.of());
    }

    private <T> List<T> toList(Iterable<T> values) {
        return java.util.stream.StreamSupport.stream(values.spliterator(), false).toList();
    }
}
