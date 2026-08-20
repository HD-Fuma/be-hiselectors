package com.fuma.hiselectors.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.service.GenerationService;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
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
class NewContentServiceTest {

    @Mock
    private GenerationService generationService;

    @Mock
    private ContentBatchAccountRepository accountRepository;

    @Mock
    private ContentFetcher fetcher;

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
                List.of(fetcher),
                classifier,
                contentRepository,
                versionRepository,
                mediaRepository,
                new ContentSnapshotFactory(),
                transactionTemplate,
                Clock.fixed(
                        Instant.parse("2026-08-20T03:00:00Z"),
                        ZoneId.of("Asia/Seoul")));
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

        List<RawContent> candidates = service.newCandidates(target);

        assertThat(candidates).containsExactly(duplicate, selected);
        verify(fetcher).fetchByAccount("instagram-account", since);
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

        assertThat(result).isEqualTo(new NewContentService.NewContentResult(1, 0));
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
        assertThat(account.getLastCollectedAt()).isEqualTo(collectedAt);
        verify(accountRepository).save(account);
    }

    @Test
    void countsFailedAccountAndDoesNotUpdateCursorWhenTransactionSaveFails() {
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

        assertThat(service.collect()).isEqualTo(
                new NewContentService.NewContentResult(0, 1));
        assertThat(account.getLastCollectedAt()).isNull();
        verify(accountRepository, never()).save(account);
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

        assertThat(service.collect()).isEqualTo(
                new NewContentService.NewContentResult(1, 1));
        verify(accountRepository).save(successfulAccount);
        verify(accountRepository, never()).save(failedAccount);
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
        return SelectorsSnsAccount.builder()
                .selectorsId(1L)
                .snsCode(SnsPlatform.INSTAGRAM)
                .accountId(accountId)
                .lastCollectedAt(lastCollectedAt)
                .build();
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

    private <T> List<T> toList(Iterable<T> values) {
        return java.util.stream.StreamSupport.stream(values.spliterator(), false).toList();
    }
}
