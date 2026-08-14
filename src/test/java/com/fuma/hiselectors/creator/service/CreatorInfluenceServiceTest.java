package com.fuma.hiselectors.creator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.creator.dto.InfluenceCandidate;
import com.fuma.hiselectors.creator.dto.DailyReportCandidatesResponse;
import com.fuma.hiselectors.creator.dto.TopPercentInfluenceResponse;
import com.fuma.hiselectors.creator.repository.CreatorPoolRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreatorInfluenceServiceTest {

    @Mock CreatorPoolRepository creatorPoolRepository;
    @Mock InfluenceScoreCalculator influenceScoreCalculator;

    @InjectMocks CreatorInfluenceService service;

    @Test
    void 후보_19명의_상위_10퍼센트는_올림해_2명을_반환한다() {
        List<InfluenceCandidate> candidates = LongStream.rangeClosed(1, 19)
                .mapToObj(this::candidate)
                .toList();
        InfluenceScoreCalculator realCalculator = new InfluenceScoreCalculator();
        when(creatorPoolRepository.findInfluenceCandidates(
                eq("BEAUTY"), eq("YOUTUBE"), eq(1), any(LocalDateTime.class)))
                .thenReturn(candidates);
        when(influenceScoreCalculator.rank(candidates))
                .thenReturn(realCalculator.rank(candidates));

        LocalDateTime beforeCutoff = LocalDateTime.now().minusDays(90);
        TopPercentInfluenceResponse result =
                service.findTopPercent("beauty", "youtube", 10, 90);
        LocalDateTime afterCutoff = LocalDateTime.now().minusDays(90);

        assertThat(result.categoryCode()).isEqualTo("BEAUTY");
        assertThat(result.snsCode()).isEqualTo("YOUTUBE");
        assertThat(result.activeWithinDays()).isEqualTo(90);
        assertThat(result.totalCandidates()).isEqualTo(19);
        assertThat(result.selectedCount()).isEqualTo(2);
        assertThat(result.creators()).hasSize(2);
        ArgumentCaptor<LocalDateTime> cutoffCaptor =
                ArgumentCaptor.forClass(LocalDateTime.class);
        verify(creatorPoolRepository).findInfluenceCandidates(
                eq("BEAUTY"), eq("YOUTUBE"), eq(1), cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue()).isBetween(beforeCutoff, afterCutoff);
    }

    @Test
    void 후보가_없으면_빈_결과를_반환한다() {
        when(creatorPoolRepository.findInfluenceCandidates(
                eq("BEAUTY"), eq("YOUTUBE"), eq(1), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(influenceScoreCalculator.rank(List.of())).thenReturn(List.of());

        TopPercentInfluenceResponse result =
                service.findTopPercent("BEAUTY", "YOUTUBE", 10, 90);

        assertThat(result.totalCandidates()).isZero();
        assertThat(result.selectedCount()).isZero();
        assertThat(result.creators()).isEmpty();
    }

    @Test
    void topPercent는_1부터_100까지만_허용한다() {
        assertThatThrownBy(() -> service.findTopPercent("BEAUTY", "YOUTUBE", 0, 90))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("topPercent는 1~100 사이여야 합니다.");
        assertThatThrownBy(() -> service.findTopPercent("BEAUTY", "YOUTUBE", 101, 90))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("topPercent는 1~100 사이여야 합니다.");
    }

    @Test
    void 카테고리와_SNS코드는_필수다() {
        assertThatThrownBy(() -> service.findTopPercent(" ", "YOUTUBE", 10, 90))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("categoryCode는 필수입니다.");
        assertThatThrownBy(() -> service.findTopPercent("BEAUTY", null, 10, 90))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("snsCode는 필수입니다.");
    }

    @Test
    void 최근_활동_기간은_1일부터_3650일까지_허용한다() {
        assertThatThrownBy(() -> service.findTopPercent(
                "BEAUTY", "YOUTUBE", 10, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("activeWithinDays는 1~3650 사이여야 합니다.");
        assertThatThrownBy(() -> service.findTopPercent(
                "BEAUTY", "YOUTUBE", 10, 3_651))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("activeWithinDays는 1~3650 사이여야 합니다.");
    }

    @Test
    void 오늘_발굴된_대상끼리_상위_퍼센트를_계산한다() {
        LocalDate selectionDate = LocalDate.of(2026, 8, 13);
        List<InfluenceCandidate> candidates = List.of(
                candidate(1L, "INSTAGRAM", 1_000L, "1.00", selectionDate),
                candidate(2L, "YOUTUBE", 100_000L, "9.00", selectionDate.minusDays(1)),
                candidate(3L, "YOUTUBE", 90_000L, "8.00", selectionDate)
        );
        InfluenceScoreCalculator realCalculator = new InfluenceScoreCalculator();
        when(creatorPoolRepository.findInfluenceCandidatesByCategory(
                eq("BEAUTY"), eq(1), any(LocalDateTime.class)))
                .thenReturn(candidates);
        when(influenceScoreCalculator.rank(any())).thenAnswer(invocation ->
                realCalculator.rank(invocation.getArgument(0)));

        DailyReportCandidatesResponse result = service.findDailyReportCandidates(
                "beauty", 50, 90, 3, selectionDate);

        assertThat(result.categoryCode()).isEqualTo("BEAUTY");
        assertThat(result.rankingPoolSize()).isEqualTo(3);
        assertThat(result.dailyTargetCount()).isEqualTo(2);
        assertThat(result.selectedCount()).isEqualTo(1);
        assertThat(result.creators()).extracting(creator -> creator.creatorId())
                .containsExactly(1L);
    }

    @Test
    void 기존_계정보다_점수가_낮아도_오늘_대상_중_상위면_선정한다() {
        LocalDate selectionDate = LocalDate.of(2026, 8, 13);
        List<InfluenceCandidate> candidates = List.of(
                candidate(1L, "YOUTUBE", 100_000L, "9.00", selectionDate.minusDays(1)),
                candidate(2L, "YOUTUBE", 1_000L, "1.00", selectionDate)
        );
        InfluenceScoreCalculator realCalculator = new InfluenceScoreCalculator();
        when(creatorPoolRepository.findInfluenceCandidatesByCategory(
                eq("BEAUTY"), eq(1), any(LocalDateTime.class)))
                .thenReturn(candidates);
        when(influenceScoreCalculator.rank(candidates))
                .thenReturn(realCalculator.rank(candidates));

        DailyReportCandidatesResponse result = service.findDailyReportCandidates(
                "BEAUTY", 10, 90, 5, selectionDate);

        assertThat(result.dailyTargetCount()).isEqualTo(1);
        assertThat(result.selectedCount()).isEqualTo(1);
        assertThat(result.creators()).extracting(creator -> creator.creatorId())
                .containsExactly(2L);
    }

    @Test
    void 오늘_지표가_갱신된_기존_계정도_일일_대상에_포함한다() {
        LocalDate selectionDate = LocalDate.of(2026, 8, 13);
        List<InfluenceCandidate> candidates = List.of(
                candidate(1L, "YOUTUBE", 100_000L, "9.00",
                        selectionDate.minusDays(10), selectionDate),
                candidate(2L, "YOUTUBE", 50_000L, "5.00",
                        selectionDate.minusDays(10), selectionDate.minusDays(1))
        );
        InfluenceScoreCalculator realCalculator = new InfluenceScoreCalculator();
        when(creatorPoolRepository.findInfluenceCandidatesByCategory(
                eq("BEAUTY"), eq(1), any(LocalDateTime.class)))
                .thenReturn(candidates);
        when(influenceScoreCalculator.rank(candidates))
                .thenReturn(realCalculator.rank(candidates));

        DailyReportCandidatesResponse result = service.findDailyReportCandidates(
                "BEAUTY", 100, 90, 5, selectionDate);

        assertThat(result.dailyTargetCount()).isEqualTo(1);
        assertThat(result.creators()).extracting(creator -> creator.creatorId())
                .containsExactly(1L);
    }

    @Test
    void 일일_선정_한도는_1부터_100까지만_허용한다() {
        assertThatThrownBy(() -> service.findDailyReportCandidates(
                "BEAUTY", 10, 90, 0, LocalDate.of(2026, 8, 13)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("dailyLimit는 1~100 사이여야 합니다.");
    }

    private InfluenceCandidate candidate(long id) {
        return new InfluenceCandidate(
                id,
                "YOUTUBE",
                "UC" + id,
                "크리에이터 " + id,
                1_000L * id,
                BigDecimal.valueOf(id),
                LocalDateTime.of(2026, 8, 1, 0, 0).plusDays(id),
                "BEAUTY",
                LocalDateTime.of(2026, 8, 1, 0, 0)
        );
    }

    private InfluenceCandidate candidate(long id, String snsCode, Long followers,
                                         String engagement, LocalDate discoveredDate) {
        return candidate(id, snsCode, followers, engagement,
                discoveredDate, discoveredDate);
    }

    private InfluenceCandidate candidate(long id, String snsCode, Long followers,
                                         String engagement, LocalDate discoveredDate,
                                         LocalDate updatedDate) {
        return new InfluenceCandidate(
                id,
                snsCode,
                snsCode + id,
                "크리에이터 " + id,
                followers,
                new BigDecimal(engagement),
                LocalDateTime.of(2026, 8, 12, 0, 0),
                "BEAUTY",
                discoveredDate.atTime(12, 0),
                updatedDate.atTime(12, 0)
        );
    }
}
