package com.fuma.hiselectors.selectors.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.content.client.YoutubeContentFetcher;
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
import com.fuma.hiselectors.penalty.model.PenaltyHistory;
import com.fuma.hiselectors.penalty.model.PenaltyStatus;
import com.fuma.hiselectors.penalty.repository.PenaltyHistoryRepository;
import com.fuma.hiselectors.selectors.dto.SelectorsDetailResponse;
import com.fuma.hiselectors.selectors.dto.SelectorsPenaltyResponse;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import com.fuma.hiselectors.selectors.repository.SelectorsGenerationRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsRoleRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import com.fuma.hiselectors.user.model.User;
import com.fuma.hiselectors.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class SelectorsServiceTest {

    private SelectorsRepository selectorsRepository;
    private PenaltyHistoryRepository penaltyHistoryRepository;
    private ApplicationRepository applicationRepository;
    private UserRepository userRepository;
    private ContentRepository contentRepository;
    private ContentEngagementRepository contentEngagementRepository;
    private ContentVersionRepository contentVersionRepository;
    private ContentMediaRepository contentMediaRepository;
    private SelectorsSnsAccountRepository selectorsSnsAccountRepository;
    private YoutubeContentFetcher youtubeContentFetcher;
    private SelectorsService selectorsService;

    @BeforeEach
    void setUp() {
        selectorsRepository = mock(SelectorsRepository.class);
        penaltyHistoryRepository = mock(PenaltyHistoryRepository.class);
        applicationRepository = mock(ApplicationRepository.class);
        userRepository = mock(UserRepository.class);
        contentRepository = mock(ContentRepository.class);
        contentEngagementRepository = mock(ContentEngagementRepository.class);
        contentVersionRepository = mock(ContentVersionRepository.class);
        contentMediaRepository = mock(ContentMediaRepository.class);
        selectorsSnsAccountRepository = mock(SelectorsSnsAccountRepository.class);
        youtubeContentFetcher = mock(YoutubeContentFetcher.class);
        selectorsService = new SelectorsService(
                selectorsRepository,
                mock(SelectorsRoleRepository.class),
                mock(SelectorsGenerationRepository.class),
                selectorsSnsAccountRepository,
                youtubeContentFetcher,
                penaltyHistoryRepository,
                applicationRepository,
                userRepository,
                contentRepository,
                contentEngagementRepository,
                contentVersionRepository,
                contentMediaRepository);
    }

    @Test
    void returnsYoutubeChannelTitleAsSnsDisplayName() {
        String channelId = "UC1111111111111111111111";
        Pageable pageable = PageRequest.of(0, 20);
        Selectors selectors = mock(Selectors.class);
        when(selectors.getId()).thenReturn(1L);
        when(selectors.getSelectorsCode()).thenReturn("SEL001");
        when(selectors.getSelectorsNickname()).thenReturn("정하린");
        when(selectorsRepository.search(null, null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(selectors), pageable, 1));

        SelectorsSnsAccount account = mock(SelectorsSnsAccount.class);
        when(account.getSelectorsId()).thenReturn(1L);
        when(account.getSnsCode()).thenReturn(SnsPlatform.YOUTUBE);
        when(account.getAccountId()).thenReturn(channelId);
        when(selectorsSnsAccountRepository.findAllBySelectorsIdInAndDeletedFalse(List.of(1L)))
                .thenReturn(List.of(account));
        when(youtubeContentFetcher.fetchChannelTitles(List.of(channelId)))
                .thenReturn(Map.of(channelId, "하린의 생활연구소"));

        var result = selectorsService.search(null, null, null, null, pageable)
                .getContent().getFirst();

        assertThat(result.snsAccountId()).isEqualTo(channelId);
        assertThat(result.snsDisplayName()).isEqualTo("하린의 생활연구소");
        verify(youtubeContentFetcher).fetchChannelTitles(List.of(channelId));
    }

    @Test
    void returnsPenaltyCountsAndBlacklistTarget() {
        Pageable pageable = PageRequest.of(0, 20);
        Selectors selectors = mock(Selectors.class);
        when(selectors.getId()).thenReturn(1L);
        when(selectors.getSelectorsCode()).thenReturn("SEL001");
        when(selectors.getSelectorsNickname()).thenReturn("tester");
        when(selectors.isBlacklisted()).thenReturn(true);
        when(selectorsRepository.searchWithPenalties(
                2L, PenaltyStatus.ACTIVE, true, pageable))
                .thenReturn(new PageImpl<>(List.of(selectors), pageable, 1));

        LocalDateTime now = LocalDateTime.now();
        PenaltyHistory released = PenaltyHistory.activate(1L, 10L, now.minusDays(3));
        released.release(now.minusDays(2));
        when(penaltyHistoryRepository.findAllBySelectorsIdsAndGenerationId(List.of(1L), 2L))
                .thenReturn(List.of(
                        PenaltyHistory.activate(1L, 11L, now.minusDays(1)),
                        PenaltyHistory.activate(1L, 12L, now),
                        released));

        Page<SelectorsPenaltyResponse> result = selectorsService.findPenalties(
                2L, PenaltyStatus.ACTIVE, true, pageable);

        assertThat(result.getContent()).singleElement().satisfies(response -> {
            assertThat(response.totalPenaltyCount()).isEqualTo(3);
            assertThat(response.activePenaltyCount()).isEqualTo(2);
            assertThat(response.blacklistTarget()).isTrue();
            assertThat(response.histories()).hasSize(3);
        });
        verify(selectorsRepository).searchWithPenalties(
                2L, PenaltyStatus.ACTIVE, true, pageable);
    }

    @Test
    void returnsDetailWithPenaltiesRecentContentsAndPerformance() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 12, 0);
        Selectors selectors = mock(Selectors.class);
        when(selectors.getId()).thenReturn(1L);
        when(selectors.getSelectorsCode()).thenReturn("SEL001");
        when(selectors.getSelectorsNickname()).thenReturn("tester");
        when(selectors.getApplicationId()).thenReturn(11L);
        when(selectors.getUserId()).thenReturn(12L);
        when(selectors.isBlacklisted()).thenReturn(true);
        when(selectorsRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(selectors));
        Application application = mock(Application.class);
        when(application.getCreatedAt()).thenReturn(now.minusDays(10));
        when(application.getPolicyAgreedAt()).thenReturn(now.minusDays(9));
        when(applicationRepository.findById(11L)).thenReturn(Optional.of(application));
        User user = mock(User.class);
        when(user.getAlimtalk()).thenReturn("y");
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        stubCollectedAccount(1L, now);

        PenaltyHistory released = PenaltyHistory.activate(1L, 10L, now.minusDays(3));
        released.release(now.minusDays(2));
        when(penaltyHistoryRepository.findAllBySelectorsIds(List.of(1L)))
                .thenReturn(List.of(
                        PenaltyHistory.activate(1L, 11L, now.minusDays(1)),
                        PenaltyHistory.activate(1L, 12L, now),
                        released));

        List<Content> contents = java.util.stream.LongStream.rangeClosed(1, 6)
                .mapToObj(id -> content(id, now.minusHours(id)))
                .toList();
        when(contentRepository
                .findAllBySelectorsIdAndDeletedFalseOrderByCreatedAtDescIdDesc(1L))
                .thenReturn(contents);
        List<ContentEngagement> engagements = List.of(
                engagement(1L, 100L, 10L, 1L),
                engagement(2L, 200L, 20L, 2L),
                engagement(3L, 0L, 0L, 0L),
                engagement(4L, 0L, 0L, 0L),
                engagement(5L, 0L, 0L, 0L),
                engagement(6L, 600L, 0L, 6L));
        when(contentEngagementRepository.findLatestByContentIds(
                List.of(1L, 2L, 3L, 4L, 5L, 6L))).thenReturn(engagements);
        ContentVersion version = mock(ContentVersion.class);
        when(version.getId()).thenReturn(101L);
        when(version.getContentId()).thenReturn(1L);
        when(contentVersionRepository.findCurrentByContentIdIn(List.of(1L, 2L, 3L, 4L, 5L)))
                .thenReturn(List.of(version));
        List<ContentMedia> titleMedia = List.of(
                textMedia(101L, "  "),
                textMedia(101L, " 실제 제목 "),
                textMedia(101L, "나중 제목"));
        when(contentMediaRepository
                .findAllByContentVersionIdInOrderByContentVersionIdAscSequenceNoAsc(
                        java.util.Set.of(101L)))
                .thenReturn(titleMedia);

        SelectorsDetailResponse result = selectorsService.findDetail(1L);

        assertThat(result.snsVerifiedAt()).isEqualTo(now.minusDays(10));
        assertThat(result.privacyAgreedAt()).isEqualTo(now.minusDays(9));
        assertThat(result.alimtalkAgreed()).isTrue();
        assertThat(result.totalPenaltyCount()).isEqualTo(3);
        assertThat(result.activePenaltyCount()).isEqualTo(2);
        assertThat(result.blacklistTarget()).isTrue();
        assertThat(result.contents()).extracting(SelectorsDetailResponse.ContentResponse::id)
                .containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(result.contents().getFirst().title()).isEqualTo("실제 제목");
        assertThat(result.contents().get(1).title()).isNull();
        assertThat(result.contents().getFirst().viewCount()).isEqualTo(100L);
        assertThat(result.contents().get(2).viewCount()).isZero();
        assertThat(result.performance()).isEqualTo(
                new SelectorsDetailResponse.PerformanceResponse(6L, 900L, 30L, 9L));
    }

    @Test
    void keepsActualZeroAndReturnsNullForAnUncollectedMetric() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 12, 0);
        Selectors selectors = mock(Selectors.class);
        Content storedContent = content(1L, now);
        ContentEngagement storedEngagement = engagement(1L, 0L, null, 0L);
        when(selectors.getId()).thenReturn(1L);
        when(selectorsRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(selectors));
        stubCollectedAccount(1L, now);
        when(contentRepository
                .findAllBySelectorsIdAndDeletedFalseOrderByCreatedAtDescIdDesc(1L))
                .thenReturn(List.of(storedContent));
        when(contentEngagementRepository.findLatestByContentIds(List.of(1L)))
                .thenReturn(List.of(storedEngagement));

        SelectorsDetailResponse response = selectorsService.findDetail(1L);
        SelectorsDetailResponse.PerformanceResponse performance = response.performance();

        assertThat(response.snsVerifiedAt()).isNull();
        assertThat(response.privacyAgreedAt()).isNull();
        assertThat(response.alimtalkAgreed()).isFalse();
        assertThat(performance).isEqualTo(
                new SelectorsDetailResponse.PerformanceResponse(1L, 0L, null, 0L));
    }

    @Test
    void ignoresExistingPerformanceUntilTheCurrentAccountIsCollected() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 12, 0);
        Selectors selectors = mock(Selectors.class);
        SelectorsSnsAccount account = mock(SelectorsSnsAccount.class);
        Content oldContent = content(1L, now.minusDays(1));
        ContentEngagement oldEngagement = engagement(1L, 100L, 10L, 1L);
        when(selectors.getId()).thenReturn(1L);
        when(selectorsRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(selectors));
        when(selectorsSnsAccountRepository.findBySelectorsIdAndDeletedFalse(1L))
                .thenReturn(Optional.of(account));
        when(contentRepository
                .findAllBySelectorsIdAndDeletedFalseOrderByCreatedAtDescIdDesc(1L))
                .thenReturn(List.of(oldContent))
                .thenReturn(List.of());
        when(contentEngagementRepository.findLatestByContentIds(List.of(1L)))
                .thenReturn(List.of(oldEngagement));
        when(account.getLastCollectedAt()).thenReturn(null).thenReturn(now);

        SelectorsDetailResponse.PerformanceResponse uncollected =
                selectorsService.findDetail(1L).performance();
        SelectorsDetailResponse.PerformanceResponse collected =
                selectorsService.findDetail(1L).performance();

        assertThat(uncollected).isEqualTo(
                new SelectorsDetailResponse.PerformanceResponse(null, null, null, null));
        assertThat(collected).isEqualTo(
                new SelectorsDetailResponse.PerformanceResponse(0L, 0L, 0L, 0L));
    }

    private void stubCollectedAccount(Long selectorsId, LocalDateTime collectedAt) {
        SelectorsSnsAccount account = mock(SelectorsSnsAccount.class);
        when(account.getLastCollectedAt()).thenReturn(collectedAt);
        when(selectorsSnsAccountRepository.findBySelectorsIdAndDeletedFalse(selectorsId))
                .thenReturn(Optional.of(account));
    }

    private Content content(Long id, LocalDateTime createdAt) {
        Content content = mock(Content.class);
        when(content.getId()).thenReturn(id);
        when(content.getSnsCode()).thenReturn(SnsPlatform.YOUTUBE);
        when(content.getContentUrl()).thenReturn("https://example.com/" + id);
        when(content.getContentType()).thenReturn(ContentType.SHORTS);
        when(content.getCreatedAt()).thenReturn(createdAt);
        return content;
    }

    private ContentEngagement engagement(
            Long contentId, Long views, Long likes, Long comments) {
        ContentEngagement engagement = mock(ContentEngagement.class);
        when(engagement.getContentId()).thenReturn(contentId);
        when(engagement.getViewCount()).thenReturn(views);
        when(engagement.getLikeCount()).thenReturn(likes);
        when(engagement.getCommentCount()).thenReturn(comments);
        return engagement;
    }

    private ContentMedia textMedia(Long versionId, String text) {
        ContentMedia media = mock(ContentMedia.class);
        when(media.getContentVersionId()).thenReturn(versionId);
        when(media.getMediaType()).thenReturn(MediaType.TEXT);
        when(media.bodyOrEmpty()).thenReturn(Map.of("text", text));
        return media;
    }
}
