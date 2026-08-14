package com.fuma.hiselectors.creator.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.creator.dto.InfluenceCandidate;
import com.fuma.hiselectors.creator.dto.InfluenceRankedCreator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class InfluenceScoreCalculatorTest {

    private final InfluenceScoreCalculator calculator = new InfluenceScoreCalculator();

    @Test
    void 팔로워_ER_최근활동을_40_40_20으로_합산해_정렬한다() {
        LocalDateTime 기준일 = LocalDateTime.of(2026, 8, 13, 0, 0);
        List<InfluenceCandidate> candidates = List.of(
                candidate(1L, 10_000L, "2.00", 기준일.minusDays(30)),
                candidate(2L, 20_000L, "1.00", 기준일.minusDays(20)),
                candidate(3L, 30_000L, "3.00", 기준일.minusDays(10))
        );

        List<InfluenceRankedCreator> result = calculator.rank(candidates);

        assertThat(result).extracting(InfluenceRankedCreator::creatorId)
                .containsExactly(3L, 2L, 1L);
        assertThat(result.getFirst().followerScore()).isEqualByComparingTo("100.00");
        assertThat(result.getFirst().engagementScore()).isEqualByComparingTo("100.00");
        assertThat(result.getFirst().recencyScore()).isEqualByComparingTo("100.00");
        assertThat(result.getFirst().influenceScore()).isEqualByComparingTo("100.00");
        assertThat(result.get(1).influenceScore()).isEqualByComparingTo("30.00");
        assertThat(result.get(2).influenceScore()).isEqualByComparingTo("20.00");
    }

    @Test
    void 종합점수가_같으면_ER_팔로워_ID_순으로_정렬한다() {
        LocalDateTime sameDate = LocalDateTime.of(2026, 8, 13, 0, 0);
        List<InfluenceCandidate> candidates = List.of(
                candidate(3L, 10_000L, "2.00", sameDate),
                candidate(2L, 20_000L, "1.00", sameDate),
                candidate(1L, 20_000L, "1.00", sameDate)
        );

        List<InfluenceRankedCreator> result = calculator.rank(candidates);

        assertThat(result).extracting(InfluenceRankedCreator::creatorId)
                .containsExactly(3L, 1L, 2L);
    }

    @Test
    void 후보가_한명이면_모든_상대점수가_100점이다() {
        InfluenceCandidate only = candidate(
                1L, 10_000L, "2.00", LocalDateTime.of(2026, 8, 13, 0, 0));

        InfluenceRankedCreator result = calculator.rank(List.of(only)).getFirst();

        assertThat(result.followerScore()).isEqualByComparingTo("100.00");
        assertThat(result.engagementScore()).isEqualByComparingTo("100.00");
        assertThat(result.recencyScore()).isEqualByComparingTo("100.00");
        assertThat(result.influenceScore()).isEqualByComparingTo("100.00");
    }

    @Test
    void 누락된_지표는_0점으로_계산한다() {
        InfluenceCandidate missing = new InfluenceCandidate(
                1L, "YOUTUBE", "UC1", "누락", null, null, null, "BEAUTY", null);

        InfluenceRankedCreator result = calculator.rank(List.of(missing)).getFirst();

        assertThat(result.influenceScore()).isEqualByComparingTo("0.00");
    }

    @Test
    void 팔로워_절대값_차이가_커도_백분위로_완화한다() {
        LocalDateTime sameDate = LocalDateTime.of(2026, 8, 13, 0, 0);
        List<InfluenceCandidate> candidates = List.of(
                candidate(1L, 1_000L, "2.00", sameDate),
                candidate(2L, 100_000L, "2.00", sameDate),
                candidate(3L, 100_000_000L, "2.00", sameDate)
        );

        List<InfluenceRankedCreator> result = calculator.rank(candidates);

        assertThat(result).extracting(InfluenceRankedCreator::followerScore)
                .containsExactly(
                        new BigDecimal("100.00"),
                        new BigDecimal("50.00"),
                        new BigDecimal("0.00"));
    }

    @Test
    void 동점은_같은_평균_백분위_점수를_받는다() {
        LocalDateTime sameDate = LocalDateTime.of(2026, 8, 13, 0, 0);
        List<InfluenceCandidate> candidates = List.of(
                candidate(1L, 10_000L, "1.00", sameDate),
                candidate(2L, 20_000L, "2.00", sameDate),
                candidate(3L, 20_000L, "2.00", sameDate)
        );

        List<InfluenceRankedCreator> result = calculator.rank(candidates);

        assertThat(result.get(0).followerScore()).isEqualByComparingTo("75.00");
        assertThat(result.get(1).followerScore()).isEqualByComparingTo("75.00");
        assertThat(result.get(0).engagementScore()).isEqualByComparingTo("75.00");
        assertThat(result.get(1).engagementScore()).isEqualByComparingTo("75.00");
        assertThat(result).extracting(InfluenceRankedCreator::recencyScore)
                .allSatisfy(score -> assertThat(score).isEqualByComparingTo("50.00"));
    }

    private InfluenceCandidate candidate(Long id, Long followers, String engagement,
                                         LocalDateTime lastContentAt) {
        return new InfluenceCandidate(
                id,
                "YOUTUBE",
                "UC" + id,
                "크리에이터 " + id,
                followers,
                new BigDecimal(engagement),
                lastContentAt,
                "BEAUTY",
                LocalDateTime.of(2026, 8, 13, 0, 0)
        );
    }
}
