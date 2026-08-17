package com.fuma.hiselectors.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
    private static final LocalDateTime CONTENT_COLLECTION_START_AT =
            LocalDateTime.of(2026, 5, 1, 0, 0);

    @Mock
    private ContentPlatformClient instagramClient;
    @Mock
    private ContentPlatformClient youtubeClient;
    @Mock
    private SelectorsContentClassifier classifier;
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
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        service = new ContentCollectionService(
                List.of(instagramClient, youtubeClient), classifier,
                accountRepository, contentRepository,
                versionRepository, mediaRepository, transactionTemplate);
    }

    @Test
    @DisplayName("최종 수집 시각 이후의 신규 셀렉터스 콘텐츠만 묶어서 저장한다")
    void collectNewSelectorsContent(CapturedOutput output) {
        LocalDateTime lastCollectedAt = LocalDateTime.of(2026, 8, 10, 0, 0);
        SelectorsSnsAccount snapshot = account(lastCollectedAt);
        SelectorsSnsAccount managedAccount = account(lastCollectedAt);
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(snapshot), Optional.of(managedAccount));
        RawContent old = raw(
                "old", "RC0001", lastCollectedAt.minusSeconds(1), List.of());
        RawContent boundary = raw(
                "boundary", "일반 게시글", lastCollectedAt, List.of());
        RawContent unrelated = raw(
                "unrelated", "일반 게시글", lastCollectedAt.plusMinutes(2), List.of());
        RawContent matching = raw(
                "matching", List.of("RC0001", "두 번째 본문"),
                lastCollectedAt.plusMinutes(3),
                List.of(
                        new RawContentMedia("image-1", MediaType.IMAGE,
                                "https://cdn.example.com/image.jpg"),
                        new RawContentMedia("video-1", MediaType.VIDEO, null)));
        when(instagramClient.collect("nike", lastCollectedAt))
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

        verify(instagramClient, times(1)).collect("nike", lastCollectedAt);
        verify(youtubeClient, never()).collect(any(), any());
        verify(classifier, never()).isSelectorsContent(old);
        verify(contentRepository, times(1)).saveAll(any());
        verify(versionRepository, times(1)).saveAll(any());
        verify(mediaRepository, times(1)).saveAll(any());
    }

    @Test
    @DisplayName("경계 시각에 다시 조회된 기존 콘텐츠는 제외하고 신규 콘텐츠만 저장한다")
    void excludeExistingContentAtCollectionBoundary() {
        LocalDateTime lastCollectedAt = LocalDateTime.of(2026, 8, 10, 0, 0);
        SelectorsSnsAccount snapshot = account(lastCollectedAt);
        SelectorsSnsAccount managedAccount = account(lastCollectedAt);
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(snapshot), Optional.of(managedAccount));
        RawContent existing = raw(
                "existing", "RC0001", lastCollectedAt, List.of());
        RawContent newContent = raw(
                "new", "RC0002", lastCollectedAt, List.of());
        when(instagramClient.collect("nike", lastCollectedAt))
                .thenReturn(new CollectionResult(2, List.of(existing, newContent)));
        when(contentRepository.findAllBySnsCodeAndSnsContentIdIn(
                SnsPlatform.INSTAGRAM, List.of("existing", "new")))
                .thenReturn(List.of(Content.builder()
                        .selectorsId(SELECTORS_ID)
                        .snsCode(SnsPlatform.INSTAGRAM)
                        .snsContentId("existing")
                        .contentUrl(existing.contentUrl())
                        .contentType(existing.contentType())
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
    @DisplayName("한 API 응답에 같은 콘텐츠가 반복되면 한 번만 저장한다")
    void excludeDuplicateContentInApiResponse() {
        LocalDateTime lastCollectedAt = LocalDateTime.of(2026, 8, 10, 0, 0);
        SelectorsSnsAccount snapshot = account(lastCollectedAt);
        SelectorsSnsAccount managedAccount = account(lastCollectedAt);
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(snapshot), Optional.of(managedAccount));
        RawContent duplicate = raw(
                "duplicate", "RC0001", lastCollectedAt, List.of());
        when(instagramClient.collect("nike", lastCollectedAt))
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
        verify(contentRepository).findAllBySnsCodeAndSnsContentIdIn(
                SnsPlatform.INSTAGRAM, List.of("duplicate"));
        verify(classifier).isSelectorsContent(duplicate);
    }

    @Test
    @DisplayName("신규 후보가 없어도 API 호출 성공 시 최종 수집 시각을 갱신한다")
    void advanceCollectionTimeWithoutNewContent() {
        LocalDateTime lastCollectedAt = LocalDateTime.of(2026, 8, 10, 0, 0);
        SelectorsSnsAccount snapshot = account(lastCollectedAt);
        SelectorsSnsAccount managedAccount = account(lastCollectedAt);
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(snapshot), Optional.of(managedAccount));
        when(instagramClient.collect("nike", lastCollectedAt))
                .thenReturn(new CollectionResult(1, List.of(raw(
                        "old", "RC0001", lastCollectedAt.minusSeconds(1), List.of()))));

        int savedCount = service.collectForAccount(ACCOUNT_ID);

        assertThat(savedCount).isZero();
        assertThat(managedAccount.getLastCollectedAt()).isAfter(lastCollectedAt);
        verifyNoInteractions(classifier);
        verify(contentRepository, never()).saveAll(any());
        verify(versionRepository, never()).saveAll(any());
        verify(mediaRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("최초 수집은 기수 시작일부터 선별하고 성공 시 수집 시각을 기록한다")
    void collectFromGenerationStartWhenLastCollectedAtIsNull() {
        SelectorsSnsAccount snapshot = account(null);
        SelectorsSnsAccount managedAccount = account(null);
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(snapshot), Optional.of(managedAccount));
        RawContent beforeGeneration = raw(
                "before", "RC0001", CONTENT_COLLECTION_START_AT.minusSeconds(1), List.of());
        RawContent generationContent = raw(
                "generation", "일반 게시글", CONTENT_COLLECTION_START_AT, List.of());
        when(instagramClient.collect("nike", CONTENT_COLLECTION_START_AT))
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
    @DisplayName("최초 외부 API 호출이 실패하면 수집 완료 시각을 기록하지 않는다")
    void keepCollectionTimeWhenApiFails() {
        SelectorsSnsAccount snapshot = account(null);
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(snapshot));
        when(instagramClient.collect("nike", CONTENT_COLLECTION_START_AT))
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
        when(instagramClient.collect("nike", lastCollectedAt))
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
        return raw("hash-target", texts, CONTENT_COLLECTION_START_AT, media);
    }

    private String contentHash(RawContent rawContent) {
        return ReflectionTestUtils.invokeMethod(service, "contentHash", rawContent);
    }

    private <T> List<T> toList(Iterable<T> values) {
        return StreamSupport.stream(values.spliterator(), false).toList();
    }
}
