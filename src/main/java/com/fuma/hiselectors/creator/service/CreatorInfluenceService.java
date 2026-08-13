package com.fuma.hiselectors.creator.service;

import com.fuma.hiselectors.creator.dto.InfluenceCandidate;
import com.fuma.hiselectors.creator.dto.InfluenceRankedCreator;
import com.fuma.hiselectors.creator.dto.DailyReportCandidatesResponse;
import com.fuma.hiselectors.creator.dto.TopPercentInfluenceResponse;
import com.fuma.hiselectors.creator.repository.CreatorPoolRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 카테고리·플랫폼별 영향력 상위 N%를 실시간으로 계산한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorInfluenceService {

    private static final int MIN_PERCENT = 1;
    private static final int MAX_PERCENT = 100;
    private static final int MIN_ACTIVE_WITHIN_DAYS = 1;
    private static final int MAX_ACTIVE_WITHIN_DAYS = 3_650;
    private static final int MIN_DAILY_LIMIT = 1;
    private static final int MAX_DAILY_LIMIT = 100;
    private static final int MAX_BRAND_SCORE = 1;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final CreatorPoolRepository creatorPoolRepository;
    private final InfluenceScoreCalculator influenceScoreCalculator;

    public TopPercentInfluenceResponse findTopPercent(
            String categoryCode, String snsCode, int topPercent, int activeWithinDays) {
        validate(categoryCode, snsCode, topPercent, activeWithinDays);

        String normalizedCategory = categoryCode.trim().toUpperCase(Locale.ROOT);
        String normalizedSnsCode = snsCode.trim().toUpperCase(Locale.ROOT);
        LocalDateTime activeAfter = LocalDateTime.now().minusDays(activeWithinDays);
        List<InfluenceCandidate> candidates = creatorPoolRepository
                .findInfluenceCandidates(
                        normalizedCategory, normalizedSnsCode, MAX_BRAND_SCORE, activeAfter);
        List<InfluenceRankedCreator> ranked = influenceScoreCalculator.rank(candidates);
        int selectedCount = ranked.isEmpty()
                ? 0
                : (int) ((ranked.size() * (long) topPercent + 99L) / 100L);
        List<InfluenceRankedCreator> selected = ranked.subList(0, selectedCount);

        return new TopPercentInfluenceResponse(
                normalizedCategory,
                normalizedSnsCode,
                topPercent,
                activeWithinDays,
                ranked.size(),
                selected.size(),
                List.copyOf(selected)
        );
    }

    /**
     * 플랫폼마다 영향력 분포가 다르므로 플랫폼 안에서 먼저 상위 N%를 계산한다.
     * 그중 기준일에 처음 등록된 계정만 합치고, 정규화된 점수로 카테고리 한도를 적용한다.
     */
    public DailyReportCandidatesResponse findDailyReportCandidates(
            String categoryCode, int topPercent, int activeWithinDays,
            int dailyLimit, LocalDate selectionDate) {
        validateCategory(categoryCode);
        validateTopPercent(topPercent);
        validateActiveWithinDays(activeWithinDays);
        validateDailyLimit(dailyLimit);

        String normalizedCategory = categoryCode.trim().toUpperCase(Locale.ROOT);
        LocalDate effectiveDate = selectionDate == null
                ? LocalDate.now(SEOUL)
                : selectionDate;
        LocalDateTime dayStart = effectiveDate.atStartOfDay();
        LocalDateTime dayEnd = effectiveDate.plusDays(1).atStartOfDay();
        LocalDateTime activeAfter = dayEnd.minusDays(activeWithinDays);
        List<InfluenceCandidate> candidates = creatorPoolRepository
                .findInfluenceCandidatesByCategory(
                        normalizedCategory, MAX_BRAND_SCORE, activeAfter);

        Map<String, List<InfluenceCandidate>> byPlatform = candidates.stream()
                .filter(candidate -> candidate.snsCode() != null
                        && !candidate.snsCode().isBlank())
                .collect(Collectors.groupingBy(InfluenceCandidate::snsCode));

        List<InfluenceRankedCreator> qualifiedToday = new ArrayList<>();
        for (List<InfluenceCandidate> platformCandidates : byPlatform.values()) {
            List<InfluenceRankedCreator> ranked = influenceScoreCalculator
                    .rank(platformCandidates);
            int platformSelectionCount = percentageCount(ranked.size(), topPercent);
            ranked.stream()
                    .limit(platformSelectionCount)
                    .filter(creator -> isDiscoveredOn(
                            creator.discoveredAt(), dayStart, dayEnd))
                    .forEach(qualifiedToday::add);
        }

        List<InfluenceRankedCreator> selected = qualifiedToday.stream()
                .sorted(dailyCandidateComparator())
                .limit(dailyLimit)
                .toList();
        int discoveredTodayCount = (int) candidates.stream()
                .filter(candidate -> isDiscoveredOn(
                        candidate.discoveredAt(), dayStart, dayEnd))
                .count();

        return new DailyReportCandidatesResponse(
                effectiveDate,
                normalizedCategory,
                topPercent,
                activeWithinDays,
                dailyLimit,
                candidates.size(),
                discoveredTodayCount,
                selected.size(),
                List.copyOf(selected)
        );
    }

    private void validate(String categoryCode, String snsCode, int topPercent,
                          int activeWithinDays) {
        validateCategory(categoryCode);
        if (snsCode == null || snsCode.isBlank()) {
            throw new IllegalArgumentException("snsCode는 필수입니다.");
        }
        validateTopPercent(topPercent);
        validateActiveWithinDays(activeWithinDays);
    }

    private void validateCategory(String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()) {
            throw new IllegalArgumentException("categoryCode는 필수입니다.");
        }
    }

    private void validateTopPercent(int topPercent) {
        if (topPercent < MIN_PERCENT || topPercent > MAX_PERCENT) {
            throw new IllegalArgumentException("topPercent는 1~100 사이여야 합니다.");
        }
    }

    private void validateActiveWithinDays(int activeWithinDays) {
        if (activeWithinDays < MIN_ACTIVE_WITHIN_DAYS
                || activeWithinDays > MAX_ACTIVE_WITHIN_DAYS) {
            throw new IllegalArgumentException(
                    "activeWithinDays는 1~3650 사이여야 합니다.");
        }
    }

    private void validateDailyLimit(int dailyLimit) {
        if (dailyLimit < MIN_DAILY_LIMIT || dailyLimit > MAX_DAILY_LIMIT) {
            throw new IllegalArgumentException("dailyLimit는 1~100 사이여야 합니다.");
        }
    }

    private int percentageCount(int size, int topPercent) {
        return size == 0 ? 0 : (int) ((size * (long) topPercent + 99L) / 100L);
    }

    private boolean isDiscoveredOn(LocalDateTime discoveredAt,
                                   LocalDateTime dayStart,
                                   LocalDateTime dayEnd) {
        return discoveredAt != null
                && !discoveredAt.isBefore(dayStart)
                && discoveredAt.isBefore(dayEnd);
    }

    private Comparator<InfluenceRankedCreator> dailyCandidateComparator() {
        Comparator<BigDecimal> descending =
                Comparator.nullsLast(Comparator.reverseOrder());
        return Comparator.comparing(
                        InfluenceRankedCreator::influenceScore, descending)
                .thenComparing(InfluenceRankedCreator::engagementScore, descending)
                .thenComparing(InfluenceRankedCreator::followerScore, descending)
                .thenComparing(InfluenceRankedCreator::creatorId);
    }
}
