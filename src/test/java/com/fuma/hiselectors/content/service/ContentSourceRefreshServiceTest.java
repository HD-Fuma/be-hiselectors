package com.fuma.hiselectors.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.client.ContentFetcher;
import com.fuma.hiselectors.content.client.dto.RawContent;
import com.fuma.hiselectors.content.client.dto.RawContentMedia;
import com.fuma.hiselectors.content.dto.ContentSourceRefreshResponse;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentEngagement;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.content.repository.ContentEngagementRepository;
import com.fuma.hiselectors.content.repository.ContentMediaRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.service.GenerationService;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class ContentSourceRefreshServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-01T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime COLLECTED_AT = LocalDateTime.of(2026, 9, 1, 12, 0);

    @Mock
    private GenerationService generationService;
    @Mock
    private ContentRepository contentRepository;
    @Mock
    private ContentVersionRepository versionRepository;
    @Mock
    private ContentMediaRepository mediaRepository;
    @Mock
    private ContentEngagementRepository engagementRepository;
    @Mock
    private SelectorsSnsAccountRepository accountRepository;
    @Mock
    private ContentFetcher youtubeFetcher;
    @Mock
    private ContentFetcher instagramFetcher;
    @Mock
    private TransactionTemplate transactionTemplate;

    private final ContentSnapshotFactory snapshotFactory = new ContentSnapshotFactory();
    private ContentSourceRefreshService service;

    @BeforeEach
    void setUp() {
        lenient().when(youtubeFetcher.supports()).thenReturn(SnsPlatform.YOUTUBE);
        lenient().when(instagramFetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        service = new ContentSourceRefreshService(
                generationService,
                contentRepository,
                versionRepository,
                mediaRepository,
                engagementRepository,
                accountRepository,
                List.of(youtubeFetcher, instagramFetcher),
                snapshotFactory,
                transactionTemplate,
                CLOCK);
    }

    @Test
    void refreshesIncompleteYoutubeContentFromStoredLink() {
        Content content = youtubeContent(11L, 210L, "FcceHtZRS5I");
        ContentVersion version = version(11L, 101L);
        ContentMedia video = ContentMedia.create(
                101L, MediaType.VIDEO, content.getContentUrl(),
                "https://i.ytimg.com/vi/FcceHtZRS5I/hqdefault.jpg",
                "FcceHtZRS5I", 0, Map.of());
        SelectorsSnsAccount account = youtubeAccount(210L, "@mama");
        RawContent raw = youtubeRaw(
                "FcceHtZRS5I", "지금 더현대서울 가야하는 이유", "쇼핑 브이로그");
        ContentFetcher.Engagement engagement =
                new ContentFetcher.Engagement(1200L, 80L, 9L, null);

        stubCurrentGeneration(List.of(content), List.of(version), List.of(video), List.of());
        when(accountRepository.findAllBySelectorsIdInAndDeletedFalse(List.of(210L)))
                .thenReturn(List.of(account));
        when(youtubeFetcher.fetchByContentIds(List.of("FcceHtZRS5I")))
                .thenReturn(List.of(new ContentFetcher.FetchResult(
                        "FcceHtZRS5I", ContentFetcher.FetchStatus.FOUND, raw, engagement)));
        when(youtubeFetcher.fetchProfile("@mama"))
                .thenReturn(new ContentFetcher.Profile("https://yt3.ggpht.com/mama.jpg", 100L, 20L));
        when(contentRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(content));
        when(versionRepository.findCurrentByContentIdIn(List.of(11L))).thenReturn(List.of(version));
        when(mediaRepository.findByContentVersionIdOrderBySequenceNoAsc(101L))
                .thenReturn(new ArrayList<>(List.of(video)));
        when(accountRepository.findBySelectorsIdAndDeletedFalseForUpdate(210L))
                .thenReturn(Optional.of(account));
        when(accountRepository.findBySelectorsIdAndDeletedFalse(210L))
                .thenReturn(Optional.of(account));
        when(mediaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(engagementRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ContentSourceRefreshResponse response = service.refresh(null);

        assertThat(response.targetCount()).isEqualTo(1);
        assertThat(response.textsUpdatedCount()).isEqualTo(1);
        assertThat(response.engagementUpdatedCount()).isEqualTo(1);
        assertThat(response.profileImageUpdatedCount()).isEqualTo(1);
        assertThat(response.failedCount()).isZero();
        assertThat(response.results()).singleElement().satisfies(item -> {
            assertThat(item.contentId()).isEqualTo(11L);
            assertThat(item.texts()).containsExactly(
                    "지금 더현대서울 가야하는 이유", "쇼핑 브이로그");
            assertThat(item.viewCount()).isEqualTo(1200L);
            assertThat(item.likeCount()).isEqualTo(80L);
            assertThat(item.commentCount()).isEqualTo(9L);
            assertThat(item.profileImageUrl()).isEqualTo("https://yt3.ggpht.com/mama.jpg");
            assertThat(item.failureReason()).isNull();
        });
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ContentMedia>> mediaCaptor = ArgumentCaptor.forClass(List.class);
        verify(mediaRepository).saveAll(mediaCaptor.capture());
        assertThat(mediaCaptor.getValue()).extracting(media -> media.bodyOrEmpty().get("text"))
                .containsExactly("지금 더현대서울 가야하는 이유", "쇼핑 브이로그");
        verify(instagramFetcher, never()).fetchByContentIds(any());
    }

    @Test
    void parsesShortsUrlWhenSnsContentIdIsALink() {
        Content content = youtubeContent(11L, 210L, "https://www.youtube.com/shorts/FcceHtZRS5I");
        ContentVersion version = version(11L, 101L);
        RawContent raw = youtubeRaw("FcceHtZRS5I", "제목", "설명");

        when(contentRepository.findById(11L)).thenReturn(Optional.of(content));
        when(accountRepository.findAllBySelectorsIdInAndDeletedFalse(List.of(210L)))
                .thenReturn(List.of(youtubeAccount(210L, "@mama")));
        when(youtubeFetcher.fetchByContentIds(List.of("FcceHtZRS5I")))
                .thenReturn(List.of(new ContentFetcher.FetchResult(
                        "FcceHtZRS5I",
                        ContentFetcher.FetchStatus.FOUND,
                        raw,
                        new ContentFetcher.Engagement(1L, 1L, 1L, null))));
        when(youtubeFetcher.fetchProfile("@mama"))
                .thenReturn(new ContentFetcher.Profile(null, null, null));
        when(contentRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(content));
        when(versionRepository.findCurrentByContentIdIn(List.of(11L))).thenReturn(List.of(version));
        when(mediaRepository.findByContentVersionIdOrderBySequenceNoAsc(101L))
                .thenReturn(new ArrayList<>());
        when(mediaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(engagementRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.findBySelectorsIdAndDeletedFalse(210L)).thenReturn(Optional.empty());

        ContentSourceRefreshResponse response = service.refresh(11L);

        assertThat(content.getSnsContentId()).isEqualTo("FcceHtZRS5I");
        assertThat(response.results().getFirst().textsUpdated()).isTrue();
        verify(contentRepository).save(content);
    }

    @Test
    void skipsCompleteCurrentGenerationContent() {
        Content content = youtubeContent(11L, 210L, "FcceHtZRS5I");
        ContentVersion version = version(11L, 101L);
        ContentMedia text = ContentMedia.create(
                101L, MediaType.TEXT, null, null, 0, Map.of("text", "이미 있는 제목"));
        ContentEngagement engagement = ContentEngagement.builder()
                .contentId(11L)
                .viewCount(10L)
                .createdAt(COLLECTED_AT.minusDays(1))
                .build();

        stubCurrentGeneration(
                List.of(content), List.of(version), List.of(text), List.of(engagement));

        ContentSourceRefreshResponse response = service.refresh(null);

        assertThat(response.targetCount()).isZero();
        verify(youtubeFetcher, never()).fetchByContentIds(any());
    }

    @Test
    void returnsNotFoundForMissingContentId() {
        when(contentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONTENT_NOT_FOUND);
    }

    @Test
    void recordsFailureWhenYoutubeContentIsMissing() {
        Content content = youtubeContent(11L, 210L, "FcceHtZRS5I");
        ContentVersion version = version(11L, 101L);

        stubCurrentGeneration(List.of(content), List.of(version), List.of(), List.of());
        when(accountRepository.findAllBySelectorsIdInAndDeletedFalse(List.of(210L)))
                .thenReturn(List.of(youtubeAccount(210L, "@mama")));
        when(youtubeFetcher.fetchByContentIds(List.of("FcceHtZRS5I")))
                .thenReturn(List.of(new ContentFetcher.FetchResult(
                        "FcceHtZRS5I", ContentFetcher.FetchStatus.NOT_FOUND, null, null)));

        ContentSourceRefreshResponse response = service.refresh(null);

        assertThat(response.failedCount()).isEqualTo(1);
        assertThat(response.results().getFirst().failureReason())
                .isEqualTo("SNS에서 콘텐츠를 찾지 못했습니다.");
        verify(engagementRepository, never()).saveAll(any());
    }

    private void stubCurrentGeneration(
            List<Content> contents,
            List<ContentVersion> versions,
            List<ContentMedia> media,
            List<ContentEngagement> engagements) {
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        when(generationService.getCurrentActivity()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(contentRepository.findAllByGenerationId(3L)).thenReturn(contents);
        List<Long> contentIds = contents.stream().map(Content::getId).toList();
        when(versionRepository.findCurrentByContentIdIn(contentIds)).thenReturn(versions);
        if (!versions.isEmpty()) {
            lenient().when(mediaRepository
                            .findAllByContentVersionIdInOrderByContentVersionIdAscSequenceNoAsc(anyCollection()))
                    .thenReturn(media);
        }
        when(engagementRepository.findLatestByContentIds(eq(contentIds))).thenReturn(engagements);
    }

    private Content youtubeContent(Long id, Long selectorsId, String snsContentId) {
        Content content = Content.builder()
                .selectorsId(selectorsId)
                .snsCode(SnsPlatform.YOUTUBE)
                .snsContentId(snsContentId)
                .contentUrl(snsContentId.startsWith("http")
                        ? snsContentId
                        : "https://www.youtube.com/shorts/" + snsContentId)
                .contentType(ContentType.SHORTS)
                .lastVersionNo(1L)
                .build();
        ReflectionTestUtils.setField(content, "id", id);
        return content;
    }

    private ContentVersion version(Long contentId, Long versionId) {
        ContentVersion version = ContentVersion.builder()
                .contentId(contentId)
                .versionNo(1L)
                .contentHash("seed-hash")
                .createdAt(LocalDateTime.of(2026, 8, 31, 18, 0))
                .build();
        ReflectionTestUtils.setField(version, "id", versionId);
        return version;
    }

    private SelectorsSnsAccount youtubeAccount(Long selectorsId, String accountId) {
        return SelectorsSnsAccount.builder()
                .selectorsId(selectorsId)
                .snsCode(SnsPlatform.YOUTUBE)
                .accountId(accountId)
                .build();
    }

    private RawContent youtubeRaw(String videoId, String title, String description) {
        return new RawContent(
                SnsPlatform.YOUTUBE,
                videoId,
                "https://www.youtube.com/watch?v=" + videoId,
                ContentType.SHORTS,
                List.of(title, description),
                LocalDateTime.of(2024, 9, 4, 9, 27, 39),
                List.of(new RawContentMedia(
                        videoId,
                        RawContentMedia.MediaType.VIDEO,
                        null,
                        List.of("https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg"))));
    }
}
