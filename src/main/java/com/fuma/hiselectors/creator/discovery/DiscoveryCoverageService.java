package com.fuma.hiselectors.creator.discovery;

import com.fuma.hiselectors.category.model.Category;
import com.fuma.hiselectors.category.model.DiscoveryKeyword;
import com.fuma.hiselectors.category.repository.CategoryRepository;
import com.fuma.hiselectors.creator.discovery.dto.DiscoveryCoverageResponse;
import com.fuma.hiselectors.creator.discovery.dto.DiscoveryCoverageResponse.CoverageStatus;
import com.fuma.hiselectors.creator.discovery.dto.DiscoveryCoverageResponse.KeywordCoverage;
import com.fuma.hiselectors.creator.repository.CreatorDiscoverySourceRepository;
import com.fuma.hiselectors.creator.repository.CreatorDiscoverySourceRepository.DiscoverySourcePair;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DiscoveryCoverageService {

    private static final int MINIMUM_KEYWORDS = 3;
    private static final BigDecimal MATURING_THRESHOLD = new BigDecimal("70.0");
    private static final BigDecimal SATURATING_THRESHOLD = new BigDecimal("85.0");

    private final CategoryRepository categoryRepository;
    private final CreatorDiscoverySourceRepository sourceRepository;

    @Transactional(readOnly = true)
    public List<DiscoveryCoverageResponse> findAll() {
        Map<String, List<DiscoverySourcePair>> pairsByCategory = sourceRepository
                .findActiveYoutubeSourcePairs().stream()
                .collect(Collectors.groupingBy(DiscoverySourcePair::getCategoryCode));

        return categoryRepository.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .map(category -> calculate(
                        category, pairsByCategory.getOrDefault(category.getCode(), List.of())))
                .toList();
    }

    private DiscoveryCoverageResponse calculate(
            Category category, List<DiscoverySourcePair> pairs) {
        List<DiscoveryKeyword> executedKeywords = category.getKeywords().stream()
                .filter(keyword -> keyword.getLastRunAt() != null)
                .sorted(Comparator.comparingInt(DiscoveryKeyword::getPriority).reversed()
                        .thenComparing(DiscoveryKeyword::getId))
                .toList();
        Set<Long> executedKeywordIds = executedKeywords.stream()
                .map(DiscoveryKeyword::getId)
                .collect(Collectors.toSet());

        Map<Long, Set<Long>> keywordIdsByCreator = new HashMap<>();
        Map<Long, Set<Long>> creatorIdsByKeyword = new HashMap<>();
        pairs.stream()
                .filter(pair -> executedKeywordIds.contains(pair.getKeywordId()))
                .forEach(pair -> {
                    keywordIdsByCreator.computeIfAbsent(
                                    pair.getCreatorId(), ignored -> new HashSet<>())
                            .add(pair.getKeywordId());
                    creatorIdsByKeyword.computeIfAbsent(
                                    pair.getKeywordId(), ignored -> new HashSet<>())
                            .add(pair.getCreatorId());
                });

        int observed = keywordIdsByCreator.size();
        int singletons = countOccurrences(keywordIdsByCreator, 1);
        int doubletons = countOccurrences(keywordIdsByCreator, 2);
        Estimate estimate = estimate(executedKeywords.size(), observed, singletons, doubletons);

        List<KeywordCoverage> keywords = executedKeywords.stream()
                .map(keyword -> keywordCoverage(
                        keyword, creatorIdsByKeyword, keywordIdsByCreator))
                .toList();

        return new DiscoveryCoverageResponse(
                category.getId(),
                category.getCode(),
                category.getName(),
                executedKeywords.size(),
                MINIMUM_KEYWORDS,
                observed,
                estimate.estimatedCreators(),
                estimate.coveragePercent(),
                singletons,
                doubletons,
                estimate.status(),
                recommendation(estimate.status(), executedKeywords.size()),
                keywords
        );
    }

    private int countOccurrences(Map<Long, Set<Long>> keywordIdsByCreator, int count) {
        return Math.toIntExact(keywordIdsByCreator.values().stream()
                .filter(keywordIds -> keywordIds.size() == count)
                .count());
    }

    private KeywordCoverage keywordCoverage(
            DiscoveryKeyword keyword,
            Map<Long, Set<Long>> creatorIdsByKeyword,
            Map<Long, Set<Long>> keywordIdsByCreator) {
        Set<Long> creatorIds = creatorIdsByKeyword.getOrDefault(keyword.getId(), Set.of());
        int exclusive = Math.toIntExact(creatorIds.stream()
                .filter(creatorId -> keywordIdsByCreator.get(creatorId).size() == 1)
                .count());
        int overlap = creatorIds.size() - exclusive;
        BigDecimal overlapPercent = creatorIds.isEmpty()
                ? BigDecimal.ZERO.setScale(1)
                : BigDecimal.valueOf(overlap * 100L)
                        .divide(BigDecimal.valueOf(creatorIds.size()), 1, RoundingMode.HALF_UP);

        return new KeywordCoverage(
                keyword.getId(),
                keyword.getKeyword(),
                keyword.getLastRunAt(),
                creatorIds.size(),
                exclusive,
                overlap,
                overlapPercent
        );
    }

    private Estimate estimate(int samples, int observed, int q1, int q2) {
        if (samples < MINIMUM_KEYWORDS) {
            return new Estimate(null, null, CoverageStatus.INSUFFICIENT_DATA);
        }
        if (observed == 0) {
            return new Estimate(BigDecimal.ZERO.setScale(1), BigDecimal.ZERO.setScale(1),
                    CoverageStatus.EXPLORING);
        }

        double unseen = ((samples - 1.0) / samples)
                * q1 * (q1 - 1.0)
                / (2.0 * (q2 + 1.0));
        BigDecimal estimated = BigDecimal.valueOf(observed + unseen)
                .setScale(1, RoundingMode.HALF_UP);
        BigDecimal coverage = BigDecimal.valueOf(observed)
                .multiply(BigDecimal.valueOf(100))
                .divide(estimated, 1, RoundingMode.HALF_UP);
        CoverageStatus status = coverage.compareTo(SATURATING_THRESHOLD) >= 0
                ? CoverageStatus.SATURATING
                : coverage.compareTo(MATURING_THRESHOLD) >= 0
                        ? CoverageStatus.MATURING
                        : CoverageStatus.EXPLORING;
        return new Estimate(estimated, coverage, status);
    }

    private String recommendation(CoverageStatus status, int executedKeywordCount) {
        return switch (status) {
            case INSUFFICIENT_DATA -> "최소 " + MINIMUM_KEYWORDS
                    + "개 키워드가 필요합니다. 현재 " + executedKeywordCount + "개가 실행됐습니다.";
            case EXPLORING -> "키워드마다 새로운 계정이 많이 발견되고 있어 탐색을 계속하는 편이 좋습니다.";
            case MATURING -> "중복 발견이 늘고 있습니다. 새로운 유형의 키워드로 한 번 더 확인해 보세요.";
            case SATURATING -> "검색 결과가 기존 계정으로 수렴하고 있습니다. 수집 중단 여부를 검토하세요.";
        };
    }

    private record Estimate(
            BigDecimal estimatedCreators,
            BigDecimal coveragePercent,
            CoverageStatus status
    ) {
    }
}
