package com.fuma.hiselectors.matching.service;

import com.fuma.hiselectors.campaign.model.CampaignProduct;
import com.fuma.hiselectors.campaign.repository.CampaignProductRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.matching.dto.SelectorMatchResponse;
import com.fuma.hiselectors.matching.repository.MatchingQueryRepository;
import com.fuma.hiselectors.matching.repository.MatchingQueryRepository.CategorySales;
import com.fuma.hiselectors.matching.repository.MatchingQueryRepository.SelectorClicks;
import com.fuma.hiselectors.performance.repository.SelectorPerformanceQueryRepository;
import com.fuma.hiselectors.performance.repository.SelectorPerformanceQueryRepository.SelectorSnsProfile;
import com.fuma.hiselectors.product.model.Product;
import com.fuma.hiselectors.product.repository.ProductRepository;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신규 상품·캠페인 카테고리에 적합한 셀렉터스를 추천한다.
 *
 * <p>실적자(해당 카테고리 확정매출 &gt; 0)를 먼저 추천한다. 점수는 <b>최근성 가중 매출</b>과
 * <b>전환율</b>(카테고리 상품 클릭 대비 확정 주문)을 후보 내 정규화해 가중 합산한 값이라,
 * 단순 매출 상위만 반복 추천되던 문제를 완화한다. 실적자가 부족할 때만 대표 카테고리가
 * 일치하는 셀렉터스로 보완한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SelectorMatchingService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int RECENT_DAYS = 90;
    private static final double RECENT_WEIGHT = 1.0;
    private static final double OLDER_WEIGHT = 0.4;

    private final MatchingQueryRepository matchingRepository;
    private final SelectorsRepository selectorsRepository;
    private final SelectorPerformanceQueryRepository performanceQueryRepository;
    private final ProductRepository productRepository;
    private final CampaignProductRepository campaignProductRepository;
    private final Clock clock;

    public List<SelectorMatchResponse> recommend(
            String category, Long productId, Long campaignId,
            LocalDate startDate, LocalDate endDate, int limit) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "시작일은 종료일보다 늦을 수 없습니다.");
        }
        Set<String> categories = resolveCategories(category, productId, campaignId);
        if (categories.isEmpty()) {
            return List.of();
        }
        int cap = limit <= 0 ? DEFAULT_LIMIT : limit;
        String categoryLabel = String.join(", ", categories);
        LocalDateTime startInclusive = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endExclusive = endDate == null ? null : endDate.plusDays(1).atStartOfDay();

        List<Ranked> performers = rankPerformers(categories, startInclusive, endExclusive, cap);

        List<SelectorMatchResponse> result = new ArrayList<>();
        Set<Long> chosenIds = new LinkedHashSet<>();
        Map<Long, Selectors> selectors = new HashMap<>();
        Map<Long, String> profileImages = new HashMap<>();
        performers.forEach(ranked -> chosenIds.add(ranked.selectorId()));

        List<Selectors> fill = performers.size() < cap
                ? fillRepresentatives(categories, chosenIds, cap - performers.size())
                : List.of();
        fill.forEach(selector -> chosenIds.add(selector.getId()));

        loadSelectorsAndProfiles(chosenIds, selectors, profileImages);

        for (Ranked ranked : performers) {
            Selectors selector = selectors.get(ranked.selectorId());
            if (selector != null) {
                result.add(performerResponse(selector, ranked, profileImages.get(selector.getId())));
            }
        }
        for (Selectors selector : fill) {
            result.add(fallbackResponse(selector, categoryLabel, profileImages.get(selector.getId())));
        }
        return result;
    }

    private List<Ranked> rankPerformers(
            Set<String> categories, LocalDateTime startInclusive, LocalDateTime endExclusive, int cap) {
        List<CategorySales> fullSales = matchingRepository.summarizeCategoryConfirmedSales(
                categories, startInclusive, endExclusive);
        if (fullSales.isEmpty()) {
            return List.of();
        }
        LocalDateTime recentStart = recentStart(startInclusive);
        Map<Long, BigDecimal> recentSales = matchingRepository.summarizeCategoryConfirmedSales(
                        categories, recentStart, endExclusive).stream()
                .collect(Collectors.toMap(CategorySales::selectorId, CategorySales::totalSales));
        Map<Long, Long> clicks = matchingRepository.countCategoryProductClicks(
                        categories, startInclusive, endExclusive).stream()
                .collect(Collectors.toMap(SelectorClicks::selectorId, SelectorClicks::clicks));

        List<Ranked> ranked = new ArrayList<>(fullSales.size());
        List<MatchScorer.Signal> signals = new ArrayList<>(fullSales.size());
        for (CategorySales row : fullSales) {
            BigDecimal total = row.totalSales();
            BigDecimal recent = recentSales.getOrDefault(row.selectorId(), BigDecimal.ZERO);
            BigDecimal older = total.subtract(recent).max(BigDecimal.ZERO);
            double recencyWeighted =
                    recent.doubleValue() * RECENT_WEIGHT + older.doubleValue() * OLDER_WEIGHT;
            long clickCount = clicks.getOrDefault(row.selectorId(), 0L);
            double conversion = clickCount == 0 ? 0.0 : (double) row.confirmedOrderCount() / clickCount;
            ranked.add(new Ranked(
                    row.selectorId(), total, row.confirmedOrderCount(),
                    conversionPercent(row.confirmedOrderCount(), clickCount), 0));
            signals.add(new MatchScorer.Signal(recencyWeighted, conversion));
        }
        int[] scores = MatchScorer.score(signals);
        for (int i = 0; i < ranked.size(); i++) {
            ranked.set(i, ranked.get(i).withScore(scores[i]));
        }
        return ranked.stream()
                .sorted(Comparator.comparingInt(Ranked::score).reversed()
                        .thenComparing(Ranked::categorySales, Comparator.reverseOrder())
                        .thenComparing(Ranked::categoryOrderCount, Comparator.reverseOrder())
                        .thenComparing(Ranked::selectorId))
                .limit(cap)
                .toList();
    }

    private List<Selectors> fillRepresentatives(
            Set<String> categories, Set<Long> excludeIds, int need) {
        if (need <= 0) {
            return List.of();
        }
        return matchingRepository.findRepresentativeCategorySelectors(categories).stream()
                .filter(selector -> !excludeIds.contains(selector.getId()))
                .limit(need)
                .toList();
    }

    private void loadSelectorsAndProfiles(
            Set<Long> ids, Map<Long, Selectors> selectors, Map<Long, String> profileImages) {
        if (ids.isEmpty()) {
            return;
        }
        selectorsRepository.findAllById(ids).stream()
                .filter(selector -> !selector.isDeleted())
                .forEach(selector -> selectors.put(selector.getId(), selector));
        performanceQueryRepository.findSnsProfiles(List.copyOf(ids)).stream()
                .filter(profile -> profile.profileImageUrl() != null)
                .forEach(profile -> profileImages.putIfAbsent(
                        profile.selectorId(), profile.profileImageUrl()));
    }

    private SelectorMatchResponse performerResponse(
            Selectors selector, Ranked ranked, String profileImageUrl) {
        return new SelectorMatchResponse(
                selector.getId(), selector.getSelectorsCode(), selector.getSelectorsNickname(),
                selector.getCategory(), profileImageUrl, ranked.categorySales(),
                ranked.categoryOrderCount(), ranked.conversionPercent(), ranked.score(),
                true, false, performerReason(ranked));
    }

    private SelectorMatchResponse fallbackResponse(
            Selectors selector, String categoryLabel, String profileImageUrl) {
        return new SelectorMatchResponse(
                selector.getId(), selector.getSelectorsCode(), selector.getSelectorsNickname(),
                selector.getCategory(), profileImageUrl, BigDecimal.ZERO, 0L, null, 0,
                true, true, String.format("대표 카테고리(%s) 일치 · 실적 없음", categoryLabel));
    }

    private String performerReason(Ranked ranked) {
        StringBuilder reason = new StringBuilder(
                String.format("확정매출 %,d원 · 주문 %d건",
                        ranked.categorySales().longValue(), ranked.categoryOrderCount()));
        if (ranked.conversionPercent() != null) {
            reason.append(String.format(" · 전환율 %s%%", ranked.conversionPercent().toPlainString()));
        }
        return reason.toString();
    }

    private LocalDateTime recentStart(LocalDateTime startInclusive) {
        LocalDateTime recent = LocalDate.now(clock).minusDays(RECENT_DAYS).atStartOfDay();
        return startInclusive != null && startInclusive.isAfter(recent) ? startInclusive : recent;
    }

    private BigDecimal conversionPercent(long orders, long clicks) {
        if (clicks == 0) {
            return null;
        }
        return BigDecimal.valueOf(orders)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(clicks), 1, RoundingMode.HALF_UP);
    }

    private Set<String> resolveCategories(String category, Long productId, Long campaignId) {
        if (productId != null) {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
            return normalize(Set.of(nullToBlank(product.getCategory())));
        }
        if (campaignId != null) {
            return campaignProductRepository.findAllByCampaignIdOrderByIdAsc(campaignId).stream()
                    .map(CampaignProduct::getProduct)
                    .map(Product::getCategory)
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        if (category != null && !category.isBlank()) {
            return normalize(Set.of(category));
        }
        throw new BusinessException(
                ErrorCode.INVALID_INPUT, "category, productId, campaignId 중 하나는 필수입니다.");
    }

    private Set<String> normalize(Set<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private record Ranked(
            Long selectorId,
            BigDecimal categorySales,
            long categoryOrderCount,
            BigDecimal conversionPercent,
            int score
    ) {
        private Ranked withScore(int newScore) {
            return new Ranked(selectorId, categorySales, categoryOrderCount, conversionPercent, newScore);
        }
    }
}
