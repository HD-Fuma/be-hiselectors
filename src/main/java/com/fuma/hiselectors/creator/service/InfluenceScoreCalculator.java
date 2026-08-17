package com.fuma.hiselectors.creator.service;

import com.fuma.hiselectors.creator.dto.InfluenceCandidate;
import com.fuma.hiselectors.creator.dto.InfluenceRankedCreator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * 같은 카테고리·플랫폼의 후보를 상대 평가해 영향력 점수를 계산한다.
 * 팔로워 40%, ER 40%, 최근 콘텐츠일의 최근성 20%를 반영한다.
 */
@Component
public class InfluenceScoreCalculator {

    private static final BigDecimal FOLLOWER_WEIGHT = new BigDecimal("0.40");
    private static final BigDecimal ENGAGEMENT_WEIGHT = new BigDecimal("0.40");
    private static final BigDecimal RECENCY_WEIGHT = new BigDecimal("0.20");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    /** 점수가 낮은 후보 수의 비율을 0~100점으로 환산한 뒤 종합 순위를 만든다. */
    public List<InfluenceRankedCreator> rank(List<InfluenceCandidate> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<Long> followerValues = sortedValues(
                candidates, InfluenceCandidate::followerCount);
        List<BigDecimal> engagementValues = sortedValues(
                candidates, InfluenceCandidate::engagementRate);
        List<java.time.LocalDateTime> recencyValues = sortedValues(
                candidates, InfluenceCandidate::lastContentAt);
        List<ScoredCandidate> scored = candidates.stream()
                .map(candidate -> score(
                        candidate, followerValues, engagementValues, recencyValues))
                .sorted(scoredComparator())
                .toList();

        List<InfluenceRankedCreator> ranked = new ArrayList<>(scored.size());
        for (int index = 0; index < scored.size(); index++) {
            ScoredCandidate item = scored.get(index);
            InfluenceCandidate candidate = item.candidate();
            ranked.add(new InfluenceRankedCreator(
                    index + 1,
                    candidate.creatorId(),
                    candidate.snsCode(),
                    candidate.accountId(),
                    candidate.creatorName(),
                    candidate.followerCount(),
                    candidate.engagementRate(),
                    candidate.lastContentAt(),
                    candidate.categoryCode(),
                    candidate.discoveredAt(),
                    item.followerScore(),
                    item.engagementScore(),
                    item.recencyScore(),
                    item.influenceScore()
            ));
        }
        return List.copyOf(ranked);
    }

    private ScoredCandidate score(
            InfluenceCandidate candidate,
            List<Long> followerValues,
            List<BigDecimal> engagementValues,
            List<java.time.LocalDateTime> recencyValues) {
        BigDecimal followerScore = percentile(candidate.followerCount(), followerValues);
        BigDecimal engagementScore = percentile(
                candidate.engagementRate(), engagementValues);
        BigDecimal recencyScore = percentile(candidate.lastContentAt(), recencyValues);
        BigDecimal influenceScore = followerScore.multiply(FOLLOWER_WEIGHT)
                .add(engagementScore.multiply(ENGAGEMENT_WEIGHT))
                .add(recencyScore.multiply(RECENCY_WEIGHT))
                .setScale(2, RoundingMode.HALF_UP);

        return new ScoredCandidate(candidate, followerScore, engagementScore,
                recencyScore, influenceScore);
    }

    /**
     * 값이 없는 지표는 0점이다. 측정 가능한 값이 하나뿐이면 100점이며,
     * 그 외에는 동점의 평균 순위를 반영한 percent-rank를 사용한다.
     */
    private <T extends Comparable<? super T>> BigDecimal percentile(T value,
                                                                    List<T> sortedValues) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (sortedValues.size() == 1) {
            return ONE_HUNDRED.setScale(2);
        }

        int lowerIndex = lowerBound(sortedValues, value);
        int upperIndex = upperBound(sortedValues, value);
        long averageRankTwice = 2L * lowerIndex + (upperIndex - lowerIndex - 1L);
        return BigDecimal.valueOf(averageRankTwice)
                .multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(2L * (sortedValues.size() - 1L)), 2,
                        RoundingMode.HALF_UP);
    }

    private <T extends Comparable<? super T>> List<T> sortedValues(
            List<InfluenceCandidate> candidates,
            Function<InfluenceCandidate, T> metric) {
        return candidates.stream()
                .map(metric)
                .filter(java.util.Objects::nonNull)
                .sorted()
                .toList();
    }

    private <T extends Comparable<? super T>> int lowerBound(List<T> values, T target) {
        int low = 0;
        int high = values.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (values.get(middle).compareTo(target) < 0) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private <T extends Comparable<? super T>> int upperBound(List<T> values, T target) {
        int low = 0;
        int high = values.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (values.get(middle).compareTo(target) <= 0) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private Comparator<ScoredCandidate> scoredComparator() {
        Comparator<BigDecimal> decimalDescending =
                Comparator.nullsLast(Comparator.reverseOrder());
        Comparator<Long> longDescending = Comparator.nullsLast(Comparator.reverseOrder());
        return Comparator.comparing(ScoredCandidate::influenceScore, decimalDescending)
                .thenComparing(item -> item.candidate().engagementRate(), decimalDescending)
                .thenComparing(item -> item.candidate().followerCount(), longDescending)
                .thenComparing(item -> item.candidate().creatorId());
    }

    private record ScoredCandidate(
            InfluenceCandidate candidate,
            BigDecimal followerScore,
            BigDecimal engagementScore,
            BigDecimal recencyScore,
            BigDecimal influenceScore
    ) {
    }
}
