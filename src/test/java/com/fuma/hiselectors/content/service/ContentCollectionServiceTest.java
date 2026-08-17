package com.fuma.hiselectors.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.classifier.SelectorsContentClassifier;
import com.fuma.hiselectors.content.client.ContentPlatformClient;
import com.fuma.hiselectors.content.client.ContentPlatformClient.CollectionResult;
import com.fuma.hiselectors.content.client.dto.RawContent;
import com.fuma.hiselectors.content.client.dto.RawContentMedia;
import com.fuma.hiselectors.content.client.dto.RawContentMedia.MediaType;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.repository.ContentMediaRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.service.GenerationService;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ContentCollectionServiceTest {

    private static final Long ACCOUNT_ID = 10L;
    private static final Long SELECTORS_ID = 20L;
    private static final LocalDateTime ACTIVE_GENERATION_START_AT =
            LocalDateTime.of(2026, 8, 1, 0, 0);

    @Mock
    private ContentPlatformClient instagramClient;
    @Mock
    private ContentPlatformClient youtubeClient;
    @Mock
    private SelectorsContentClassifier classifier;
    @Mock
    private GenerationService generationService;
    @Mock
    private SelectorsSnsAccountRepository accountRepository;
    @Mock
    private ContentRepository contentRepository;
    @Mock
    private ContentVersionRepository versionRepository;
    @Mock
    private ContentMediaRepository mediaRepository;
    @Mock
    private TransactionTemplate transactionTemplate;

    private ContentCollectionService service;

    @BeforeEach
    void setUp() {
        lenient().when(instagramClient.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        lenient().when(youtubeClient.supports()).thenReturn(SnsPlatform.YOUTUBE);
        lenient().when(generationService.getActive())
                .thenReturn(activeGeneration(ACTIVE_GENERATION_START_AT));
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        service = new ContentCollectionService(
                List.of(instagramClient, youtubeClient), classifier,
                generationService, accountRepository, contentRepository,
                versionRepository, mediaRepository, transactionTemplate);
    }

    @Test
    @DisplayName("이후 수집도 활성 기수 시작 시각부터 한 번 조회한다")
    void collectFromActiveGenerationStartWhenLastCollectedAtExists() {
        LocalDateTime lastCollectedAt = LocalDateTime.of(2026, 8, 10, 0, 0);
        Generation activeGeneration = activeGeneration(ACTIVE_GENERATION_START_AT);
        when(generationService.getActive()).thenReturn(activeGeneration);
        SelectorsSnsAccount snapshot = account(lastCollectedAt);
        SelectorsSnsAccount managedAccount = account(lastCollectedAt);
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(snapshot), Optional.of(managedAccount));
        when(instagramClient.collect("nike", activeGeneration.getStartDate()))
                .thenReturn(new CollectionResult(0, List.of()));

        service.collectForAccount(ACCOUNT_ID);

        verify(generationService, times(1)).getActive();
        verify(instagramClient, times(1))
                .collect("nike", activeGeneration.getStartDate());
    }

    @Test
    @DisplayName("활성 기수 시작 시각 이후의 신규 셀렉터스 콘텐츠만 묶어서 저장한다")
    void collectNewSelectorsContent(CapturedOutput output) {
        LocalDateTime lastCollectedAt = LocalDateTime.of(2026, 8, 10, 0, 0);
        SelectorsSnsAccount snapshot = account(lastCollectedAt);
        SelectorsSnsAccount managedAccount = account(lastCollectedAt);
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(snapshot), Optional.of(managedAccount));
        RawContent old = raw(
                "old", "RC0001", ACTIVE_GENERATION_START_AT.minusSeconds(1), List.of());
        RawContent boundary = raw(
                "boundary", "일반 게시글", ACTIVE_GENERATION_START_AT, List.of());
        RawContent unrelated = raw(
                "unrelated", "일반 게시글", lastCollectedAt.plusMinutes(2), List.of());
        RawContent matching = raw(
                "matching", List.of("RC0001", "두 번째 본문"),
                lastCollectedAt.plusMinutes(3),
                List.of(
                        new RawContentMedia("image-1", MediaType.IMAGE,
                                "https://cdn.example.com/image.jpg"),
                        new RawContentMedia("video-1", MediaType.VIDEO, null)));
        when(instagramClient.collect("nike", ACTIVE_GENERATION_START_AT))
                .thenReturn(new CollectionResult(
                        4, List.of(old, boundary, unrelated, matching)));
        when(classifier.isSelectorsContent(boundary)).thenReturn(false);
        when(classifier.isSelectorsContent(unrelated)).thenReturn(false);
        when(classifier.isSelectorsContent(matching)).thenReturn(true);

        AtomicReference<List<Content>> savedContents = new AtomicReference<>();
        AtomicReference<List<ContentVersion>> savedVersions = new AtomicReference<>();
        AtomicReference<List<ContentMedia>> savedMedia = new AtomicReference<>();
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

        int savedCount = service.collectForAccount(ACCOUNT_ID);

        assertThat(savedCount).isEqualTo(1);
        assertThat(savedContents.get()).singleElement().satisfies(content -> {
            assertThat(content.getSelectorsId()).isEqualTo(SELECTORS_ID);
            assertThat(content.getSnsCode()).isEqualTo(SnsPlatform.INSTAGRAM);
            assertThat(content.getSnsContentId()).isEqualTo("matching");
            assertThat(content.getContentUrl()).isEqualTo(matching.contentUrl());
            assertThat(content.getContentType()).isEqualTo(ContentType.FEED);
            assertThat(content.getLastVersionNo()).isEqualTo(1L);
        });
        assertThat(savedVersions.get()).singleElement().satisfies(version -> {
            assertThat(version.getContentId()).isEqualTo(100L);
            assertThat(version.getVersionNo()).isEqualTo(1L);
            assertThat(version.getContentHash()).isEqualTo(
                    "9db7511c084f6b395468bdd7a01bdc9fe8563d48657ea6a24c12c4c3bb442405");
            assertThat(version.getCreatedAt()).isEqualTo(managedAccount.getLastCollectedAt());
        });
        assertThat(savedMedia.get())
                .extracting(ContentMedia::getMediaType)
                .containsExactly(
                        ContentMedia.MediaType.TEXT,
                        ContentMedia.MediaType.TEXT,
                        ContentMedia.MediaType.IMAGE,
                        ContentMedia.MediaType.VIDEO);
        assertThat(savedMedia.get())
                .extracting(ContentMedia::getBody)
                .startsWith("RC0001", "두 번째 본문");
        assertThat(savedMedia.get().get(2).getMediaUrl())
                .isEqualTo("https://cdn.example.com/image.jpg");
        assertThat(savedMedia.get().get(3).getMediaUrl()).isNull();
        assertThat(savedMedia.get())
                .extracting(ContentMedia::getSequenceNo)
                .containsExactly(0, 1, 2, 3);
        assertThat(savedMedia.get())
                .extracting(ContentMedia::getSnsMediaId)
                .containsExactly(null, null, "image-1", "video-1");
        assertThat(managedAccount.getLastCollectedAt()).isAfter(lastCollectedAt);
        assertThat(output).contains(
                "콘텐츠 수집 결과 | 플랫폼=INSTAGRAM | 계정=nike | API 조회=4건 "
                        + "| 현재 기수=4건 | 셀렉터스 게시글=1건");

        verify(instagramClient, times(1)).collect("nike", ACTIVE_GENERATION_START_AT);
        verify(youtubeClient, never()).collect(any(), any());
        verify(classifier, never()).isSelectorsContent(old);
        verify(contentRepository, times(1)).saveAll(any());
        verify(versionRepository, times(1)).saveAll(any());
        verify(mediaRepository, times(1)).saveAll(any());
    }

    @Test
    @DisplayName("기수 시작 경계에 다시 조회된 기존 콘텐츠는 제외하고 신규 콘텐츠만 저장한다")
    void excludeExistingContentAtCollectionBoundary() {
        LocalDateTime lastCollectedAt = LocalDateTime.of(2026, 8, 10, 0, 0);
        SelectorsSnsAccount snapshot = account(lastCollectedAt);
        SelectorsSnsAccount managedAccount = account(lastCollectedAt);
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(snapshot), Optional.of(managedAccount));
        RawContent existing = raw(
                "existing", "RC0001", ACTIVE_GENERATION_START_AT, List.of());
        RawContent newContent = raw(
                "new", "RC0002", ACTIVE_GENERATION_START_AT, List.of());
        when(instagramClient.collect("nike", ACTIVE_GENERATION_START_AT))
                .thenReturn(new CollectionResult(2, List.of(existing, newContent)));
        Content existingContent = Content.builder()
                .selectorsId(SELECTORS_ID)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId("existing")
                .contentUrl(existing.contentUrl())
                .contentType(existing.contentType())
                .build();
        ReflectionTestUtils.setField(existingContent, "id", 100L);
        when(contentRepository.findAllBySelectorsIdAndSnsCodeAndCreatedAtGreaterThanEqual(
                SELECTORS_ID, SnsPlatform.INSTAGRAM, ACTIVE_GENERATION_START_AT))
                .thenReturn(List.of(existingContent));
        when(versionRepository.findLatestByContentIdIn(List.of(100L)))
                .thenReturn(List.of(ContentVersion.builder()
                        .contentId(100L)
                        .versionNo(1L)
                        .contentHash(contentHash(existing))
                        .createdAt(lastCollectedAt)
                        .build()));
        when(classifier.isSelectorsContent(newContent)).thenReturn(true);

        AtomicReference<List<Content>> savedContents = new AtomicReference<>();
        when(contentRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Content> values = toList(invocation.getArgument(0));
            savedContents.set(values);
            return values;
        });

        int savedCount = service.collectForAccount(ACCOUNT_ID);

        assertThat(savedCount).isEqualTo(1);
        assertThat(savedContents.get())
                .extracting(Content::getSnsContentId)
                .containsExactly("new");
        verify(classifier, never()).isSelectorsContent(existing);
        verify(classifier).isSelectorsContent(newContent);
    }

    @Test
    @DisplayName("기존 콘텐츠 내용이 바뀌면 v2와 전체 미디어 스냅샷을 저장한다")
    void saveNextVersionWhenExistingContentChanged() {
        LocalDateTime lastCollectedAt = LocalDateTime.of(2026, 8, 10, 0, 0);
        SelectorsSnsAccount snapshot = account(lastCollectedAt);
        SelectorsSnsAccount managedAccount = account(lastCollectedAt);
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(snapshot), Optional.of(managedAccount));

        RawContent previous = raw(
                "existing",
                "이전 본문",
                ACTIVE_GENERATION_START_AT,
                List.of(new RawContentMedia(
                        "image-1", MediaType.IMAGE,
                        "https://cdn.example.com/image-1.jpg")));
        RawContent changed = raw(
                "existing",
                List.of("수정된 본문", "추가된 본문"),
                ACTIVE_GENERATION_START_AT,
                List.of(
                        new RawContentMedia(
                                "image-2", MediaType.IMAGE,
                                "https://cdn.example.com/image-2.jpg"),
                        new RawContentMedia(
                                "video-1", MediaType.VIDEO,
                                "https://cdn.example.com/video-1.mp4")));
        when(instagramClient.collect("nike", ACTIVE_GENERATION_START_AT))
                .thenReturn(new CollectionResult(1, List.of(changed)));

        Content existingContent = Content.builder()
                .selectorsId(SELECTORS_ID)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId("existing")
                .contentUrl(changed.contentUrl())
                .contentType(changed.contentType())
                .build();
        ReflectionTestUtils.setField(existingContent, "id", 100L);
        when(contentRepository.findAllBySelectorsIdAndSnsCodeAndCreatedAtGreaterThanEqual(
                SELECTORS_ID, SnsPlatform.INSTAGRAM, ACTIVE_GENERATION_START_AT))
                .thenReturn(List.of(existingContent));
        when(versionRepository.findLatestByContentIdIn(List.of(100L)))
                .thenReturn(List.of(ContentVersion.builder()
                        .contentId(100L)
                        .versionNo(1L)
                        .contentHash(contentHash(previous))
                        .createdAt(lastCollectedAt)
                        .build()));

        AtomicReference<List<ContentVersion>> savedVersions = new AtomicReference<>();
        AtomicReference<List<ContentMedia>> savedMedia = new AtomicReference<>();
        when(versionRepository.saveAll(any())).thenAnswer(invocation -> {
            List<ContentVersion> values = toList(invocation.getArgument(0));
            ReflectionTestUtils.setField(values.getFirst(), "id", 201L);
            savedVersions.set(values);
            return values;
        });
        when(mediaRepository.saveAll(any())).thenAnswer(invocation -> {
            List<ContentMedia> values = toList(invocation.getArgument(0));
            savedMedia.set(values);
            return values;
        });

        int savedCount = service.collectForAccount(ACCOUNT_ID);

        assertThat(savedCount).isZero();
        assertThat(existingContent.getLastVersionNo()).isEqualTo(2L);
        assertThat(savedVersions.get()).singleElement().satisfies(version -> {
            assertThat(version.getContentId()).isEqualTo(100L);
            assertThat(version.getVersionNo()).isEqualTo(2L);
            assertThat(version.getContentHash()).isEqualTo(contentHash(changed));
            assertThat(version.getCreatedAt()).isEqualTo(managedAccount.getLastCollectedAt());
        });
        assertThat(savedMedia.get())
                .extracting(ContentMedia::getMediaType,
                        ContentMedia::getBody,
                        ContentMedia::getSnsMediaId,
                        ContentMedia::getSequenceNo)
                .containsExactly(
                        tuple(
                                ContentMedia.MediaType.TEXT, "수정된 본문", null, 0),
                        tuple(
                                ContentMedia.MediaType.TEXT, "추가된 본문", null, 1),
                        tuple(
                                ContentMedia.MediaType.IMAGE, null, "image-2", 2),
                        tuple(
                                ContentMedia.MediaType.VIDEO, null, "video-1", 3));
        assertThat(savedMedia.get())
                .extracting(ContentMedia::getMediaUrl)
                .containsExactly(
                        null,
                        null,
                        "https://cdn.example.com/image-2.jpg",
                        "https://cdn.example.com/video-1.mp4");
        verify(classifier, never()).isSelectorsContent(changed);
        verify(instagramClient, times(1)).collect("nike", ACTIVE_GENERATION_START_AT);
    }

    @Test
    @DisplayName("기존 콘텐츠 내용이 같으면 버전과 미디어를 저장하지 않는다")
    void skipExistingContentWhenHashUnchanged() {
        LocalDateTime lastCollectedAt = LocalDateTime.of(2026, 8, 10, 0, 0);
        SelectorsSnsAccount snapshot = account(lastCollectedAt);
        SelectorsSnsAccount managedAccount = account(lastCollectedAt);
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(snapshot), Optional.of(managedAccount));

        RawContent unchanged = raw(
                "existing",
                "같은 본문",
                ACTIVE_GENERATION_START_AT,
                List.of(new RawContentMedia(
                        "image-1", MediaType.IMAGE,
                        "https://cdn.example.com/refreshed-image-url.jpg")));
        when(instagramClient.collect("nike", ACTIVE_GENERATION_START_AT))
                .thenReturn(new CollectionResult(1, List.of(unchanged)));

        Content existingContent = Content.builder()
                .selectorsId(SELECTORS_ID)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId("existing")
                .contentUrl(unchanged.contentUrl())
                .contentType(unchanged.contentType())
                .build();
        ReflectionTestUtils.setField(existingContent, "id", 100L);
        when(contentRepository.findAllBySelectorsIdAndSnsCodeAndCreatedAtGreaterThanEqual(
                SELECTORS_ID, SnsPlatform.INSTAGRAM, ACTIVE_GENERATION_START_AT))
                .thenReturn(List.of(existingContent));
        when(versionRepository.findLatestByContentIdIn(List.of(100L)))
                .thenReturn(List.of(ContentVersion.builder()
                        .contentId(100L)
                        .versionNo(1L)
                        .contentHash(contentHash(unchanged))
                        .createdAt(lastCollectedAt)
                        .build()));

        int savedCount = service.collectForAccount(ACCOUNT_ID);

        assertThat(savedCount).isZero();
        assertThat(existingContent.getLastVersionNo()).isEqualTo(1L);
        verifyNoInteractions(classifier);
        verify(contentRepository, never()).saveAll(any());
        verify(versionRepository, never()).saveAll(any());
        verify(mediaRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("SNS 조회 결과에서 사라진 기존 콘텐츠를 삭제 상태로 변경한다")
    void markMissingExistingContentAsDeleted() {
        LocalDateTime lastCollectedAt = LocalDateTime.of(2026, 8, 10, 0, 0);
        SelectorsSnsAccount snapshot = account(lastCollectedAt);
        SelectorsSnsAccount managedAccount = account(lastCollectedAt);
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(snapshot), Optional.of(managedAccount));

        RawContent collected = raw(
                "collected", "일반 게시글", ACTIVE_GENERATION_START_AT, List.of());
        when(instagramClient.collect("nike", ACTIVE_GENERATION_START_AT))
                .thenReturn(new CollectionResult(1, List.of(collected)));
        when(classifier.isSelectorsContent(collected)).thenReturn(false);

        Content missingContent = Content.builder()
                .selectorsId(SELECTORS_ID)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId("missing")
                .contentUrl("https://www.instagram.com/p/missing")
                .contentType(ContentType.FEED)
                .build();
        ReflectionTestUtils.setField(missingContent, "id", 100L);
        when(contentRepository.findAllBySelectorsIdAndSnsCodeAndCreatedAtGreaterThanEqual(
                SELECTORS_ID, SnsPlatform.INSTAGRAM, ACTIVE_GENERATION_START_AT))
                .thenReturn(List.of(missingContent));

        int savedCount = service.collectForAccount(ACCOUNT_ID);

        assertThat(savedCount).isZero();
        assertThat(missingContent.isDeleted()).isTrue();
        verify(contentRepository, never()).saveAll(any());
        verify(versionRepository, never()).saveAll(any());
        verify(mediaRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("SNS 응답에 존재하는 콘텐츠는 수집 후보 범위 밖이어도 삭제하지 않는다")
    void keepExistingContentWhenPresentOutsideCandidateRange() {
        LocalDateTime lastCollectedAt = LocalDateTime.of(2026, 8, 10, 0, 0);
        SelectorsSnsAccount snapshot = account(lastCollectedAt);
        SelectorsSnsAccount managedAccount = account(lastCollectedAt);
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(snapshot), Optional.of(managedAccount));

        RawContent present = raw(
                "existing", "본문", ACTIVE_GENERATION_START_AT.minusSeconds(1), List.of());
        when(instagramClient.collect("nike", ACTIVE_GENERATION_START_AT))
                .thenReturn(new CollectionResult(1, List.of(present)));

        Content existingContent = Content.builder()
                .selectorsId(SELECTORS_ID)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId("existing")
                .contentUrl(present.contentUrl())
                .contentType(present.contentType())
                .build();
        when(contentRepository.findAllBySelectorsIdAndSnsCodeAndCreatedAtGreaterThanEqual(
                SELECTORS_ID, SnsPlatform.INSTAGRAM, ACTIVE_GENERATION_START_AT))
                .thenReturn(List.of(existingContent));

        service.collectForAccount(ACCOUNT_ID);

        assertThat(existingContent.isDeleted()).isFalse();
        verifyNoInteractions(classifier, versionRepository, mediaRepository);
    }

    @Test
    @DisplayName("빈 SNS 조회 결과가 정상 반환되면 해당 범위의 기존 콘텐츠를 모두 삭제 상태로 변경한다")
    void markAllExistingContentsDeletedWhenSuccessfulResultIsEmpty() {
        LocalDateTime lastCollectedAt = LocalDateTime.of(2026, 8, 10, 0, 0);
        SelectorsSnsAccount snapshot = account(lastCollectedAt);
        SelectorsSnsAccount managedAccount = account(lastCollectedAt);
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(snapshot), Optional.of(managedAccount));
        when(instagramClient.collect("nike", ACTIVE_GENERATION_START_AT))
                .thenReturn(new CollectionResult(0, List.of()));

        Content activeContent = Content.builder()
                .selectorsId(SELECTORS_ID)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId("active")
                .contentUrl("https://www.instagram.com/p/active")
                .contentType(ContentType.FEED)
                .build();
        Content alreadyDeletedContent = Content.builder()
                .selectorsId(SELECTORS_ID)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId("already-deleted")
                .contentUrl("https://www.instagram.com/p/already-deleted")
                .contentType(ContentType.FEED)
                .build();
        alreadyDeletedContent.markDeleted();
        when(contentRepository.findAllBySelectorsIdAndSnsCodeAndCreatedAtGreaterThanEqual(
                SELECTORS_ID, SnsPlatform.INSTAGRAM, ACTIVE_GENERATION_START_AT))
                .thenReturn(List.of(activeContent, alreadyDeletedContent));

        int savedCount = service.collectForAccount(ACCOUNT_ID);

        assertThat(savedCount).isZero();
        assertThat(activeContent.isDeleted()).isTrue();
        assertThat(alreadyDeletedContent.isDeleted()).isTrue();
        assertThat(managedAccount.getLastCollectedAt()).isAfter(lastCollectedAt);
        verify(instagramClient, times(1)).collect("nike", ACTIVE_GENERATION_START_AT);
        verifyNoInteractions(classifier);
        verify(contentRepository, never()).saveAll(any());
        verifyNoInteractions(versionRepository, mediaRepository);
    }

    @Test
    @DisplayName("활성 기수 시작 전에 저장된 콘텐츠는 수집 결과에 없어도 삭제하지 않는다")
    void keepContentStoredBeforeActiveGeneration() {
        LocalDateTime lastCollectedAt = LocalDateTime.of(2026, 8, 10, 0, 0);
        SelectorsSnsAccount snapshot = account(lastCollectedAt);
        SelectorsSnsAccount managedAccount = account(lastCollectedAt);
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(snapshot), Optional.of(managedAccount));
        when(instagramClient.collect("nike", ACTIVE_GENERATION_START_AT))
                .thenReturn(new CollectionResult(0, List.of()));

        Content previousGenerationContent = Content.builder()
                .selectorsId(SELECTORS_ID)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId("previous-generation")
                .contentUrl("https://www.instagram.com/p/previous-generation")
                .contentType(ContentType.FEED)
                .build();
        Content generationBoundaryContent = Content.builder()
                .selectorsId(SELECTORS_ID)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId("generation-boundary")
                .contentUrl("https://www.instagram.com/p/generation-boundary")
                .contentType(ContentType.FEED)
                .build();
        ReflectionTestUtils.setField(
                previousGenerationContent, "createdAt",
                ACTIVE_GENERATION_START_AT.minusNanos(1));
        ReflectionTestUtils.setField(
                generationBoundaryContent, "createdAt", ACTIVE_GENERATION_START_AT);
        when(contentRepository.findAllBySelectorsIdAndSnsCodeAndCreatedAtGreaterThanEqual(
                SELECTORS_ID, SnsPlatform.INSTAGRAM, ACTIVE_GENERATION_START_AT))
                .thenReturn(List.of(generationBoundaryContent));

        service.collectForAccount(ACCOUNT_ID);

        assertThat(previousGenerationContent.isDeleted()).isFalse();
        assertThat(generationBoundaryContent.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("삭제된 콘텐츠가 같은 내용으로 다시 조회되면 버전 추가 없이 복구한다")
    void restoreDeletedContentWithoutNewVersionWhenHashUnchanged() {
        LocalDateTime lastCollectedAt = LocalDateTime.of(2026, 8, 10, 0, 0);
        SelectorsSnsAccount snapshot = account(lastCollectedAt);
        SelectorsSnsAccount managedAccount = account(lastCollectedAt);
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(snapshot), Optional.of(managedAccount));

        RawContent reappeared = raw(
                "existing", "같은 본문", ACTIVE_GENERATION_START_AT, List.of());
        when(instagramClient.collect("nike", ACTIVE_GENERATION_START_AT))
                .thenReturn(new CollectionResult(1, List.of(reappeared)));

        Content deletedContent = Content.builder()
                .selectorsId(SELECTORS_ID)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId("existing")
                .contentUrl(reappeared.contentUrl())
                .contentType(reappeared.contentType())
                .build();
        ReflectionTestUtils.setField(deletedContent, "id", 100L);
        deletedContent.markDeleted();
        when(contentRepository.findAllBySelectorsIdAndSnsCodeAndCreatedAtGreaterThanEqual(
                SELECTORS_ID, SnsPlatform.INSTAGRAM, ACTIVE_GENERATION_START_AT))
                .thenReturn(List.of(deletedContent));
        when(versionRepository.findLatestByContentIdIn(List.of(100L)))
                .thenReturn(List.of(ContentVersion.builder()
                        .contentId(100L)
                        .versionNo(1L)
                        .contentHash(contentHash(reappeared))
                        .createdAt(lastCollectedAt)
                        .build()));

        int savedCount = service.collectForAccount(ACCOUNT_ID);

        assertThat(savedCount).isZero();
        assertThat(deletedContent.isDeleted()).isFalse();
        assertThat(deletedContent.getLastVersionNo()).isEqualTo(1L);
        verify(versionRepository, never()).saveAll(any());
        verify(mediaRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("삭제된 콘텐츠가 변경된 내용으로 다시 조회되면 복구하고 다음 버전을 저장한다")
    void restoreDeletedContentAndSaveNextVersionWhenHashChanged() {
        LocalDateTime lastCollectedAt = LocalDateTime.of(2026, 8, 10, 0, 0);
        SelectorsSnsAccount snapshot = account(lastCollectedAt);
        SelectorsSnsAccount managedAccount = account(lastCollectedAt);
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(snapshot), Optional.of(managedAccount));

        RawContent previous = raw(
                "existing", "이전 본문", ACTIVE_GENERATION_START_AT, List.of());
        RawContent reappeared = raw(
                "existing", "수정된 본문", ACTIVE_GENERATION_START_AT, List.of());
        when(instagramClient.collect("nike", ACTIVE_GENERATION_START_AT))
                .thenReturn(new CollectionResult(1, List.of(reappeared)));

        Content deletedContent = Content.builder()
                .selectorsId(SELECTORS_ID)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId("existing")
                .contentUrl(reappeared.contentUrl())
                .contentType(reappeared.contentType())
                .build();
        ReflectionTestUtils.setField(deletedContent, "id", 100L);
        deletedContent.markDeleted();
        when(contentRepository.findAllBySelectorsIdAndSnsCodeAndCreatedAtGreaterThanEqual(
                SELECTORS_ID, SnsPlatform.INSTAGRAM, ACTIVE_GENERATION_START_AT))
                .thenReturn(List.of(deletedContent));
        when(versionRepository.findLatestByContentIdIn(List.of(100L)))
                .thenReturn(List.of(ContentVersion.builder()
                        .contentId(100L)
                        .versionNo(1L)
                        .contentHash(contentHash(previous))
                        .createdAt(lastCollectedAt)
                        .build()));

        AtomicReference<List<ContentVersion>> savedVersions = new AtomicReference<>();
        AtomicReference<List<ContentMedia>> savedMedia = new AtomicReference<>();
        when(versionRepository.saveAll(any())).thenAnswer(invocation -> {
            List<ContentVersion> values = toList(invocation.getArgument(0));
            ReflectionTestUtils.setField(values.getFirst(), "id", 201L);
            savedVersions.set(values);
            return values;
        });
        when(mediaRepository.saveAll(any())).thenAnswer(invocation -> {
            List<ContentMedia> values = toList(invocation.getArgument(0));
            savedMedia.set(values);
            return values;
        });

        int savedCount = service.collectForAccount(ACCOUNT_ID);

        assertThat(savedCount).isZero();
        assertThat(deletedContent.isDeleted()).isFalse();
        assertThat(deletedContent.getLastVersionNo()).isEqualTo(2L);
        assertThat(savedVersions.get())
                .extracting(ContentVersion::getContentId,
                        ContentVersion::getVersionNo,
                        ContentVersion::getContentHash)
                .containsExactly(tuple(100L, 2L, contentHash(reappeared)));
        assertThat(savedMedia.get())
                .extracting(ContentMedia::getContentVersionId,
                        ContentMedia::getBody,
                        ContentMedia::getSequenceNo)
                .containsExactly(tuple(201L, "수정된 본문", 0));
        verifyNoInteractions(classifier);
    }

    @Test
    @DisplayName("기존 콘텐츠의 최신 버전이 없으면 일관성 오류가 발생한다")
    void failWhenLatestVersionIsMissing() {
        LocalDateTime lastCollectedAt = LocalDateTime.of(2026, 8, 10, 0, 0);
        SelectorsSnsAccount snapshot = account(lastCollectedAt);
        SelectorsSnsAccount managedAccount = account(lastCollectedAt);
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(snapshot), Optional.of(managedAccount));

        RawContent existing = raw(
                "existing", "본문", ACTIVE_GENERATION_START_AT, List.of());
        when(instagramClient.collect("nike", ACTIVE_GENERATION_START_AT))
                .thenReturn(new CollectionResult(1, List.of(existing)));

        Content existingContent = Content.builder()
                .selectorsId(SELECTORS_ID)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId("existing")
                .contentUrl(existing.contentUrl())
                .contentType(existing.contentType())
                .build();
        ReflectionTestUtils.setField(existingContent, "id", 100L);
        when(contentRepository.findAllBySelectorsIdAndSnsCodeAndCreatedAtGreaterThanEqual(
                SELECTORS_ID, SnsPlatform.INSTAGRAM, ACTIVE_GENERATION_START_AT))
                .thenReturn(List.of(existingContent));
        when(versionRepository.findLatestByContentIdIn(List.of(100L)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.collectForAccount(ACCOUNT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "콘텐츠의 최신 버전을 찾을 수 없습니다. contentId=100, versionNo=1");

        assertThat(existingContent.getLastVersionNo()).isEqualTo(1L);
        assertThat(managedAccount.getLastCollectedAt()).isEqualTo(lastCollectedAt);
        verifyNoInteractions(classifier);
        verify(versionRepository, never()).saveAll(any());
        verify(mediaRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("한 수집 결과에서 신규·변경·미변경·삭제·복구 콘텐츠를 함께 처리한다")
    void handleNewChangedUnchangedDeletedAndRestoredContentsTogether() {
        LocalDateTime lastCollectedAt = LocalDateTime.of(2026, 8, 10, 0, 0);
        SelectorsSnsAccount snapshot = account(lastCollectedAt);
        SelectorsSnsAccount managedAccount = account(lastCollectedAt);
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(snapshot), Optional.of(managedAccount));

        RawContent newContent = raw(
                "new", "신규 본문", ACTIVE_GENERATION_START_AT, List.of());
        RawContent previousChanged = raw(
                "changed", "이전 본문", ACTIVE_GENERATION_START_AT, List.of());
        RawContent changed = raw(
                "changed", "수정 본문", ACTIVE_GENERATION_START_AT, List.of());
        RawContent unchanged = raw(
                "unchanged", "동일 본문", ACTIVE_GENERATION_START_AT, List.of());
        RawContent restored = raw(
                "restored", "복구 본문", ACTIVE_GENERATION_START_AT, List.of());
        RawContent unrelated = raw(
                "unrelated", "일반 게시글", ACTIVE_GENERATION_START_AT, List.of());
        when(instagramClient.collect("nike", ACTIVE_GENERATION_START_AT))
                .thenReturn(new CollectionResult(
                        5, List.of(newContent, changed, unchanged, restored, unrelated)));
        when(classifier.isSelectorsContent(newContent)).thenReturn(true);
        when(classifier.isSelectorsContent(unrelated)).thenReturn(false);

        Content changedContent = Content.builder()
                .selectorsId(SELECTORS_ID)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId("changed")
                .contentUrl(changed.contentUrl())
                .contentType(changed.contentType())
                .build();
        Content unchangedContent = Content.builder()
                .selectorsId(SELECTORS_ID)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId("unchanged")
                .contentUrl(unchanged.contentUrl())
                .contentType(unchanged.contentType())
                .build();
        Content restoredContent = Content.builder()
                .selectorsId(SELECTORS_ID)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId("restored")
                .contentUrl(restored.contentUrl())
                .contentType(restored.contentType())
                .build();
        Content missingContent = Content.builder()
                .selectorsId(SELECTORS_ID)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId("missing")
                .contentUrl("https://www.instagram.com/p/missing")
                .contentType(ContentType.FEED)
                .build();
        ReflectionTestUtils.setField(changedContent, "id", 101L);
        ReflectionTestUtils.setField(unchangedContent, "id", 102L);
        ReflectionTestUtils.setField(restoredContent, "id", 103L);
        ReflectionTestUtils.setField(missingContent, "id", 104L);
        restoredContent.markDeleted();

        AtomicBoolean transactionActive = new AtomicBoolean();
        doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            transactionActive.set(true);
            try {
                return callback.doInTransaction(null);
            } finally {
                transactionActive.set(false);
            }
        }).when(transactionTemplate).execute(any());
        when(contentRepository.findAllBySelectorsIdAndSnsCodeAndCreatedAtGreaterThanEqual(
                SELECTORS_ID, SnsPlatform.INSTAGRAM, ACTIVE_GENERATION_START_AT))
                .thenAnswer(invocation -> {
                    assertThat(transactionActive).isTrue();
                    return List.of(
                            changedContent, unchangedContent,
                            restoredContent, missingContent);
                });
        when(versionRepository.findLatestByContentIdIn(List.of(101L, 102L, 103L)))
                .thenReturn(List.of(
                        ContentVersion.builder()
                                .contentId(101L)
                                .versionNo(1L)
                                .contentHash(contentHash(previousChanged))
                                .createdAt(lastCollectedAt)
                                .build(),
                        ContentVersion.builder()
                                .contentId(102L)
                                .versionNo(1L)
                                .contentHash(contentHash(unchanged))
                                .createdAt(lastCollectedAt)
                                .build(),
                        ContentVersion.builder()
                                .contentId(103L)
                                .versionNo(1L)
                                .contentHash(contentHash(restored))
                                .createdAt(lastCollectedAt)
                                .build()));

        AtomicReference<List<Content>> savedNewContents = new AtomicReference<>();
        when(contentRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Content> values = toList(invocation.getArgument(0));
            ReflectionTestUtils.setField(values.getFirst(), "id", 200L);
            savedNewContents.set(values);
            return values;
        });
        List<ContentVersion> savedVersions = new ArrayList<>();
        when(versionRepository.saveAll(any())).thenAnswer(invocation -> {
            List<ContentVersion> values = toList(invocation.getArgument(0));
            for (ContentVersion value : values) {
                ReflectionTestUtils.setField(
                        value, "id", 300L + savedVersions.size());
                savedVersions.add(value);
            }
            return values;
        });
        List<ContentMedia> savedMedia = new ArrayList<>();
        when(mediaRepository.saveAll(any())).thenAnswer(invocation -> {
            List<ContentMedia> values = toList(invocation.getArgument(0));
            savedMedia.addAll(values);
            return values;
        });

        int savedCount = service.collectForAccount(ACCOUNT_ID);

        assertThat(savedCount).isEqualTo(1);
        assertThat(savedNewContents.get())
                .extracting(Content::getSnsContentId)
                .containsExactly("new");
        assertThat(changedContent.getLastVersionNo()).isEqualTo(2L);
        assertThat(unchangedContent.getLastVersionNo()).isEqualTo(1L);
        assertThat(unchangedContent.isDeleted()).isFalse();
        assertThat(restoredContent.isDeleted()).isFalse();
        assertThat(restoredContent.getLastVersionNo()).isEqualTo(1L);
        assertThat(missingContent.isDeleted()).isTrue();
        assertThat(transactionActive).isFalse();
        assertThat(savedVersions)
                .extracting(ContentVersion::getContentId,
                        ContentVersion::getVersionNo,
                        ContentVersion::getContentHash)
                .containsExactly(
                        tuple(
                                200L, 1L, contentHash(newContent)),
                        tuple(
                                101L, 2L, contentHash(changed)));
        assertThat(savedVersions)
                .extracting(ContentVersion::getCreatedAt)
                .containsOnly(managedAccount.getLastCollectedAt());
        assertThat(savedMedia)
                .extracting(ContentMedia::getContentVersionId,
                        ContentMedia::getBody,
                        ContentMedia::getSequenceNo)
                .containsExactly(
                        tuple(300L, "신규 본문", 0),
                        tuple(301L, "수정 본문", 0));
        verify(classifier).isSelectorsContent(newContent);
        verify(classifier).isSelectorsContent(unrelated);
        verify(classifier, never()).isSelectorsContent(changed);
        verify(classifier, never()).isSelectorsContent(unchanged);
        verify(classifier, never()).isSelectorsContent(restored);
        verify(versionRepository, times(1))
                .findLatestByContentIdIn(List.of(101L, 102L, 103L));
        verify(contentRepository, times(1))
                .findAllBySelectorsIdAndSnsCodeAndCreatedAtGreaterThanEqual(
                        SELECTORS_ID, SnsPlatform.INSTAGRAM,
                        ACTIVE_GENERATION_START_AT);
        verify(transactionTemplate, times(1)).execute(any());
        verify(instagramClient, times(1)).collect("nike", ACTIVE_GENERATION_START_AT);
        verify(youtubeClient, never()).collect(any(), any());
    }

    @Test
    @DisplayName("한 API 응답에 같은 콘텐츠가 반복되면 한 번만 저장한다")
    void excludeDuplicateContentInApiResponse() {
        LocalDateTime lastCollectedAt = LocalDateTime.of(2026, 8, 10, 0, 0);
        SelectorsSnsAccount snapshot = account(lastCollectedAt);
        SelectorsSnsAccount managedAccount = account(lastCollectedAt);
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(snapshot), Optional.of(managedAccount));
        RawContent duplicate = raw(
                "duplicate", "RC0001", ACTIVE_GENERATION_START_AT, List.of());
        when(instagramClient.collect("nike", ACTIVE_GENERATION_START_AT))
                .thenReturn(new CollectionResult(2, List.of(duplicate, duplicate)));
        when(classifier.isSelectorsContent(duplicate)).thenReturn(true);

        AtomicReference<List<Content>> savedContents = new AtomicReference<>();
        when(contentRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Content> values = toList(invocation.getArgument(0));
            savedContents.set(values);
            return values;
        });

        int savedCount = service.collectForAccount(ACCOUNT_ID);

        assertThat(savedCount).isEqualTo(1);
        assertThat(savedContents.get())
                .extracting(Content::getSnsContentId)
                .containsExactly("duplicate");
        verify(contentRepository)
                .findAllBySelectorsIdAndSnsCodeAndCreatedAtGreaterThanEqual(
                        SELECTORS_ID, SnsPlatform.INSTAGRAM,
                        ACTIVE_GENERATION_START_AT);
        verify(classifier).isSelectorsContent(duplicate);
    }

    @Test
    @DisplayName("범위 밖 중복 콘텐츠가 먼저 있어도 범위 안 콘텐츠를 저장한다")
    void keepInRangeContentWhenOutOfRangeDuplicateAppearsFirst() {
        LocalDateTime lastCollectedAt = LocalDateTime.of(2026, 8, 10, 0, 0);
        SelectorsSnsAccount snapshot = account(lastCollectedAt);
        SelectorsSnsAccount managedAccount = account(lastCollectedAt);
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(snapshot), Optional.of(managedAccount));
        RawContent outOfRange = raw(
                "duplicate", "이전 본문",
                ACTIVE_GENERATION_START_AT.minusSeconds(1), List.of());
        RawContent inRange = raw(
                "duplicate", "현재 본문", ACTIVE_GENERATION_START_AT, List.of());
        when(instagramClient.collect("nike", ACTIVE_GENERATION_START_AT))
                .thenReturn(new CollectionResult(2, List.of(outOfRange, inRange)));
        when(classifier.isSelectorsContent(inRange)).thenReturn(true);

        AtomicReference<List<Content>> savedContents = new AtomicReference<>();
        when(contentRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Content> values = toList(invocation.getArgument(0));
            savedContents.set(values);
            return values;
        });

        int savedCount = service.collectForAccount(ACCOUNT_ID);

        assertThat(savedCount).isEqualTo(1);
        assertThat(savedContents.get())
                .extracting(Content::getSnsContentId)
                .containsExactly("duplicate");
        verify(classifier, never()).isSelectorsContent(outOfRange);
        verify(classifier).isSelectorsContent(inRange);
    }

    @Test
    @DisplayName("신규 후보가 없어도 API 호출 성공 시 최종 수집 시각을 갱신한다")
    void advanceCollectionTimeWithoutNewContent() {
        LocalDateTime lastCollectedAt = LocalDateTime.of(2026, 8, 10, 0, 0);
        SelectorsSnsAccount snapshot = account(lastCollectedAt);
        SelectorsSnsAccount managedAccount = account(lastCollectedAt);
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(snapshot), Optional.of(managedAccount));
        when(instagramClient.collect("nike", ACTIVE_GENERATION_START_AT))
                .thenReturn(new CollectionResult(1, List.of(raw(
                        "old", "RC0001",
                        ACTIVE_GENERATION_START_AT.minusSeconds(1), List.of()))));

        int savedCount = service.collectForAccount(ACCOUNT_ID);

        assertThat(savedCount).isZero();
        assertThat(managedAccount.getLastCollectedAt()).isAfter(lastCollectedAt);
        verifyNoInteractions(classifier);
        verify(contentRepository, never()).saveAll(any());
        verify(versionRepository, never()).saveAll(any());
        verify(mediaRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("마지막 수집 시각이 없어도 활성 기수 시작 시각부터 선별한다")
    void collectFromGenerationStartWhenLastCollectedAtIsNull() {
        SelectorsSnsAccount snapshot = account(null);
        SelectorsSnsAccount managedAccount = account(null);
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(snapshot), Optional.of(managedAccount));
        RawContent beforeGeneration = raw(
                "before", "RC0001", ACTIVE_GENERATION_START_AT.minusSeconds(1), List.of());
        RawContent generationContent = raw(
                "generation", "일반 게시글", ACTIVE_GENERATION_START_AT, List.of());
        when(instagramClient.collect("nike", ACTIVE_GENERATION_START_AT))
                .thenReturn(new CollectionResult(
                        2, List.of(beforeGeneration, generationContent)));
        when(classifier.isSelectorsContent(generationContent))
                .thenReturn(false);

        int savedCount = service.collectForAccount(ACCOUNT_ID);

        assertThat(savedCount).isZero();
        assertThat(managedAccount.getLastCollectedAt()).isNotNull();
        verify(classifier, never()).isSelectorsContent(beforeGeneration);
        verify(classifier).isSelectorsContent(generationContent);
        verify(contentRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("외부 API 호출이 실패하면 수집 완료 시각을 기록하지 않는다")
    void keepCollectionTimeWhenApiFails() {
        SelectorsSnsAccount snapshot = account(null);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(snapshot));
        when(instagramClient.collect("nike", ACTIVE_GENERATION_START_AT))
                .thenThrow(new IllegalStateException("API failure"));

        assertThatThrownBy(() -> service.collectForAccount(ACCOUNT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("API failure");

        assertThat(snapshot.getLastCollectedAt()).isNull();
        verifyNoInteractions(transactionTemplate, contentRepository,
                versionRepository, mediaRepository);
    }

    @Test
    @DisplayName("미디어 저장이 실패하면 최종 수집 시각을 갱신하지 않는다")
    void keepCollectionTimeWhenPersistenceFails() {
        LocalDateTime lastCollectedAt = LocalDateTime.of(2026, 8, 10, 0, 0);
        SelectorsSnsAccount snapshot = account(lastCollectedAt);
        SelectorsSnsAccount managedAccount = account(lastCollectedAt);
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(snapshot), Optional.of(managedAccount));
        RawContent matching = raw(
                "matching", "RC0001", lastCollectedAt.plusMinutes(1), List.of());
        when(instagramClient.collect("nike", ACTIVE_GENERATION_START_AT))
                .thenReturn(new CollectionResult(1, List.of(matching)));
        when(classifier.isSelectorsContent(matching)).thenReturn(true);
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
                .thenThrow(new IllegalStateException("DB failure"));

        assertThatThrownBy(() -> service.collectForAccount(ACCOUNT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DB failure");

        assertThat(managedAccount.getLastCollectedAt()).isEqualTo(lastCollectedAt);
    }

    @Test
    @DisplayName("TEXT 순서가 바뀌면 콘텐츠 해시가 달라진다")
    void distinguishTextOrderInContentHash() {
        RawContent first = hashTarget(
                List.of("first", "second"), List.of());
        RawContent reordered = hashTarget(
                List.of("second", "first"), List.of());

        assertThat(contentHash(first)).isNotEqualTo(contentHash(reordered));
    }

    @Test
    @DisplayName("미디어 순서가 바뀌면 콘텐츠 해시가 달라진다")
    void distinguishMediaOrderInContentHash() {
        RawContentMedia image = new RawContentMedia(
                "image-1", MediaType.IMAGE, "https://cdn.example.com/image.jpg");
        RawContentMedia video = new RawContentMedia(
                "video-1", MediaType.VIDEO, "https://cdn.example.com/video.mp4");

        RawContent first = hashTarget(List.of("caption"), List.of(image, video));
        RawContent reordered = hashTarget(List.of("caption"), List.of(video, image));

        assertThat(contentHash(first)).isNotEqualTo(contentHash(reordered));
    }

    @Test
    @DisplayName("미디어 유형이 바뀌면 콘텐츠 해시가 달라진다")
    void distinguishMediaTypeInContentHash() {
        RawContent image = hashTarget(List.of("caption"), List.of(
                new RawContentMedia("media-1", MediaType.IMAGE, null)));
        RawContent video = hashTarget(List.of("caption"), List.of(
                new RawContentMedia("media-1", MediaType.VIDEO, null)));

        assertThat(contentHash(image)).isNotEqualTo(contentHash(video));
    }

    @Test
    @DisplayName("SNS 미디어 ID가 바뀌면 콘텐츠 해시가 달라진다")
    void distinguishSnsMediaIdInContentHash() {
        RawContent first = hashTarget(List.of("caption"), List.of(
                new RawContentMedia("media-1", MediaType.IMAGE, null)));
        RawContent changed = hashTarget(List.of("caption"), List.of(
                new RawContentMedia("media-2", MediaType.IMAGE, null)));

        assertThat(contentHash(first)).isNotEqualTo(contentHash(changed));
    }

    @Test
    @DisplayName("미디어 URL만 바뀌면 콘텐츠 해시는 유지된다")
    void ignoreMediaUrlInContentHash() {
        RawContent first = hashTarget(List.of("caption"), List.of(
                new RawContentMedia(
                        "media-1", MediaType.IMAGE, "https://cdn.example.com/first.jpg")));
        RawContent changedUrl = hashTarget(List.of("caption"), List.of(
                new RawContentMedia(
                        "media-1", MediaType.IMAGE, "https://cdn.example.com/second.jpg")));

        assertThat(contentHash(first)).isEqualTo(contentHash(changedUrl));
    }

    private SelectorsSnsAccount account(LocalDateTime lastCollectedAt) {
        SelectorsSnsAccount account = SelectorsSnsAccount.builder()
                .selectorsId(SELECTORS_ID)
                .snsCode(SnsPlatform.INSTAGRAM)
                .accountId("nike")
                .lastCollectedAt(lastCollectedAt)
                .build();
        ReflectionTestUtils.setField(account, "id", ACCOUNT_ID);
        return account;
    }

    private Generation activeGeneration(LocalDateTime startDate) {
        return Generation.builder()
                .startDate(startDate)
                .build();
    }

    private RawContent raw(
            String snsContentId,
            String caption,
            LocalDateTime createdAt,
            List<RawContentMedia> media) {
        return raw(snsContentId, List.of(caption), createdAt, media);
    }

    private RawContent raw(
            String snsContentId,
            List<String> texts,
            LocalDateTime createdAt,
            List<RawContentMedia> media) {
        return new RawContent(
                SnsPlatform.INSTAGRAM,
                snsContentId,
                "https://www.instagram.com/p/" + snsContentId,
                ContentType.FEED,
                texts,
                createdAt,
                media);
    }

    private RawContent hashTarget(
            List<String> texts, List<RawContentMedia> media) {
        return raw("hash-target", texts, ACTIVE_GENERATION_START_AT, media);
    }

    private String contentHash(RawContent rawContent) {
        return ReflectionTestUtils.invokeMethod(service, "contentHash", rawContent);
    }

    private <T> List<T> toList(Iterable<T> values) {
        return StreamSupport.stream(values.spliterator(), false).toList();
    }
}
