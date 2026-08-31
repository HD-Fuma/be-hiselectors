package com.fuma.hiselectors.matching.service;

import com.fuma.hiselectors.campaign.model.CampaignProduct;
import com.fuma.hiselectors.campaign.repository.CampaignProductRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.matching.dto.SelectorMatchResponse;
import com.fuma.hiselectors.matching.repository.MatchingQueryRepository;
import com.fuma.hiselectors.matching.repository.MatchingQueryRepository.CategorySales;
import com.fuma.hiselectors.performance.repository.SelectorPerformanceQueryRepository;
import com.fuma.hiselectors.performance.repository.SelectorPerformanceQueryRepository.SelectorSnsProfile;
import com.fuma.hiselectors.product.model.Product;
import com.fuma.hiselectors.product.repository.ProductRepository;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
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
 * <p>기준: 해당 카테고리에서의 과거 확정 매출(주 신호) + 대표 카테고리 일치(실적이 없는
 * 신규 셀렉터스 커버). 매출 상위 우선, 동률이면 대표 카테고리 일치·주문 수로 정렬한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SelectorMatchingService {

    private static final int DEFAULT_LIMIT = 20;

    private final MatchingQueryRepository matchingRepository;
    private final SelectorsRepository selectorsRepository;
    private final SelectorPerformanceQueryRepository performanceQueryRepository;
    private final ProductRepository productRepository;
    private final CampaignProductRepository campaignProductRepository;

    /**
     * 추천 대상 카테고리를 결정한다. category / productId / campaignId 중 하나로 지정한다.
     * productId·campaignId 를 주면 서버가 상품 카테고리를 도출한다.
     */
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
        LocalDateTime startInclusive = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endExclusive = endDate == null ? null : endDate.plusDays(1).atStartOfDay();

        List<CategorySales> ranking = matchingRepository.summarizeCategoryConfirmedSales(
                categories, startInclusive, endExclusive);
        Map<Long, CategorySales> salesById = ranking.stream()
                .collect(Collectors.toMap(CategorySales::selectorId, Function.identity()));
        Set<Long> representativeIds = matchingRepository
                .findRepresentativeCategorySelectors(categories).stream()
                .map(Selectors::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        String categoryLabel = String.join(", ", categories);

        LinkedHashSet<Long> candidateIds = new LinkedHashSet<>();
        ranking.forEach(row -> candidateIds.add(row.selectorId()));
        candidateIds.addAll(representativeIds);
        if (candidateIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Selectors> selectors = selectorsRepository.findAllById(candidateIds).stream()
                .filter(selector -> !selector.isDeleted())
                .collect(Collectors.toMap(Selectors::getId, Function.identity()));
        Map<Long, SelectorSnsProfile> profiles = performanceQueryRepository
                .findSnsProfiles(List.copyOf(candidateIds)).stream()
                .collect(Collectors.toMap(
                        SelectorSnsProfile::selectorId,
                        Function.identity(),
                        (existing, ignored) -> existing));

        int cap = limit <= 0 ? DEFAULT_LIMIT : limit;
        return candidateIds.stream()
                .map(selectors::get)
                .filter(java.util.Objects::nonNull)
                .map(selector -> toResponse(
                        selector,
                        salesById.get(selector.getId()),
                        representativeIds.contains(selector.getId()),
                        categoryLabel,
                        profiles.get(selector.getId())))
                .sorted(Comparator
                        .comparing(SelectorMatchResponse::categorySales,
                                Comparator.reverseOrder())
                        .thenComparing(SelectorMatchResponse::representativeMatch,
                                Comparator.reverseOrder())
                        .thenComparing(SelectorMatchResponse::categoryOrderCount,
                                Comparator.reverseOrder())
                        .thenComparing(SelectorMatchResponse::selectorId))
                .limit(cap)
                .toList();
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

    private SelectorMatchResponse toResponse(
            Selectors selector,
            CategorySales sales,
            boolean representativeMatch,
            String category,
            SelectorSnsProfile profile) {
        BigDecimal categorySales = sales == null ? BigDecimal.ZERO : sales.totalSales();
        long orderCount = sales == null ? 0L : sales.confirmedOrderCount();
        return new SelectorMatchResponse(
                selector.getId(),
                selector.getSelectorsCode(),
                selector.getSelectorsNickname(),
                selector.getCategory(),
                profile == null ? null : profile.profileImageUrl(),
                categorySales,
                orderCount,
                representativeMatch,
                matchReason(category, categorySales, orderCount, representativeMatch));
    }

    private String matchReason(
            String category, BigDecimal categorySales, long orderCount, boolean representativeMatch) {
        if (categorySales.signum() > 0) {
            return String.format("%s 카테고리 확정매출 %,d원 · 주문 %d건",
                    category, categorySales.longValue(), orderCount);
        }
        if (representativeMatch) {
            return String.format("대표 카테고리(%s) 일치", category);
        }
        return category + " 카테고리 참여 이력";
    }
}
