package com.fuma.hiselectors.selectors.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentEngagement;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.content.repository.ContentEngagementRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.penalty.model.PenaltyHistory;
import com.fuma.hiselectors.penalty.model.PenaltyStatus;
import com.fuma.hiselectors.penalty.repository.PenaltyHistoryRepository;
import com.fuma.hiselectors.selectors.dto.SelectorsDetailResponse;
import com.fuma.hiselectors.selectors.dto.SelectorsPenaltyResponse;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsGenerationRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsRoleRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import java.time.LocalDateTime;
import java.util.List;
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
    private ContentRepository contentRepository;
    private ContentEngagementRepository contentEngagementRepository;
    private SelectorsService selectorsService;

    @BeforeEach
    void setUp() {
        selectorsRepository = mock(SelectorsRepository.class);
        penaltyHistoryRepository = mock(PenaltyHistoryRepository.class);
        contentRepository = mock(ContentRepository.class);
        contentEngagementRepository = mock(ContentEngagementRepository.class);
        selectorsService = new SelectorsService(
                selectorsRepository,
                mock(SelectorsRoleRepository.class),
                mock(SelectorsGenerationRepository.class),
                mock(SelectorsSnsAccountRepository.class),
                penaltyHistoryRepository,
                contentRepository,
                contentEngagementRepository);
    }

    @Test
    void returnsPenaltyCountsAndBlacklistTarget() {
        Pageable pageable = PageRequest.of(0, 20);
        Selectors selectors = mock(Selectors.class);
        when(selectors.getId()).thenReturn(1L);
        when(selectors.getSelectorsCode()).thenReturn("SEL001");
        when(selectors.getSelectorsNickname()).thenReturn("tester");
        when(selectorsRepository.searchWithPenalties(
                2L, PenaltyStatus.ACTIVE, true, 3L, pageable))
                .thenReturn(new PageImpl<>(List.of(selectors), pageable, 1));

        LocalDateTime now = LocalDateTime.now();
        PenaltyHistory released = PenaltyHistory.activate(1L, 10L, now.minusDays(3));
        released.release(now.minusDays(2));
        when(penaltyHistoryRepository.findAllBySelectorsIds(List.of(1L)))
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
                2L, PenaltyStatus.ACTIVE, true, 3L, pageable);
    }

    @Test
    void returnsDetailWithPenaltiesRecentContentsAndPerformance() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 12, 0);
        Selectors selectors = mock(Selectors.class);
        when(selectors.getId()).thenReturn(1L);
        when(selectors.getSelectorsCode()).thenReturn("SEL001");
        when(selectors.getSelectorsNickname()).thenReturn("tester");
        when(selectorsRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(selectors));

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
                engagement(6L, 600L, null, 6L));
        when(contentEngagementRepository.findLatestByContentIds(
                List.of(1L, 2L, 3L, 4L, 5L, 6L))).thenReturn(engagements);

        SelectorsDetailResponse result = selectorsService.findDetail(1L);

        assertThat(result.totalPenaltyCount()).isEqualTo(3);
        assertThat(result.activePenaltyCount()).isEqualTo(2);
        assertThat(result.blacklistTarget()).isTrue();
        assertThat(result.contents()).extracting(SelectorsDetailResponse.ContentResponse::id)
                .containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(result.contents().getFirst().viewCount()).isEqualTo(100L);
        assertThat(result.contents().get(2).viewCount()).isNull();
        assertThat(result.performance()).isEqualTo(
                new SelectorsDetailResponse.PerformanceResponse(6, 900, 30, 9));
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
}
