package com.fuma.hiselectors.performance.service;

import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.performance.dto.PerformanceMetricsResponse;
import com.fuma.hiselectors.performance.dto.PerformanceSummaryResponse;
import com.fuma.hiselectors.performance.dto.PerformanceTrendResponse;
import com.fuma.hiselectors.performance.dto.ProductPerformanceListResponse;
import com.fuma.hiselectors.performance.dto.ProductPerformanceResponse;
import com.fuma.hiselectors.performance.repository.PerformanceQueryRepository;
import com.fuma.hiselectors.performance.repository.PerformanceQueryRepository.DailyClick;
import com.fuma.hiselectors.performance.repository.PerformanceQueryRepository.DailyPurchase;
import com.fuma.hiselectors.performance.repository.PerformanceQueryRepository.ProductClick;
import com.fuma.hiselectors.performance.repository.PerformanceQueryRepository.ProductPurchase;
import com.fuma.hiselectors.performance.repository.PerformanceQueryRepository.PurchaseSummary;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.service.SelectorAccessService;
import com.fuma.hiselectors.settlement.service.CommissionRateCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PerformanceService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int TOP_PRODUCT_LIMIT = 3;

    private final SelectorAccessService selectorAccessService;
    private final ApplicationRepository applicationRepository;
    private final CommissionRateCalculator commissionRateCalculator;
    private final PerformanceQueryRepository performanceQueryRepository;
    private final Clock clock;

    public PerformanceSummaryResponse getSummary(String loginId, YearMonth requestedMonth) {
        Selectors selectors = selectorAccessService.requireCurrent(loginId);
        YearMonth activityMonth = resolveActivityMonth(requestedMonth);
        BigDecimal settlementRate = getSettlementRate(selectors);

        PerformanceMetricsResponse metrics = getMetrics(
                selectors.getId(), activityMonth, settlementRate);
        PerformanceMetricsResponse previousMetrics = getMetrics(
                selectors.getId(), activityMonth.minusMonths(1), settlementRate);
        List<ProductPerformanceResponse> products = getProducts(
                selectors.getId(), activityMonth, settlementRate);

        return new PerformanceSummaryResponse(
                activityMonth,
                settlementRate,
                metrics,
                previousMetrics,
                getTrends(selectors.getId(), activityMonth),
                products.stream().limit(TOP_PRODUCT_LIMIT).toList());
    }

    public ProductPerformanceListResponse getProducts(
            String loginId, YearMonth requestedMonth) {
        Selectors selectors = selectorAccessService.requireCurrent(loginId);
        YearMonth activityMonth = resolveActivityMonth(requestedMonth);
        BigDecimal settlementRate = getSettlementRate(selectors);
        List<ProductPerformanceResponse> products = getProducts(
                selectors.getId(), activityMonth, settlementRate);
        long conversionCount = products.stream()
                .mapToLong(ProductPerformanceResponse::conversionCount)
                .sum();
        return new ProductPerformanceListResponse(
                activityMonth, conversionCount, products.size(), products);
    }

    private PerformanceMetricsResponse getMetrics(
            Long selectorsId, YearMonth activityMonth, BigDecimal settlementRate) {
        Period period = Period.of(activityMonth);
        long clickCount = performanceQueryRepository.countProductClicks(
                selectorsId, period.startInclusive(), period.endExclusive());
        PurchaseSummary purchases = performanceQueryRepository.summarizeConfirmedPurchases(
                selectorsId, period.startInclusive(), period.endExclusive());
        long conversionAmount = requireWholeWon(purchases.conversionAmount());
        return new PerformanceMetricsResponse(
                calculateSettlementAmount(conversionAmount, settlementRate),
                conversionAmount,
                purchases.conversionCount(),
                clickCount,
                calculateConversionRate(purchases.conversionCount(), clickCount));
    }

    private List<PerformanceTrendResponse> getTrends(Long selectorsId, YearMonth activityMonth) {
        Period period = Period.of(activityMonth);
        Map<Integer, DailyClick> clicksByDay = new HashMap<>();
        performanceQueryRepository.findDailyProductClicks(
                        selectorsId, period.startInclusive(), period.endExclusive())
                .forEach(row -> clicksByDay.put(row.dayOfMonth(), row));
        Map<Integer, DailyPurchase> purchasesByDay = new HashMap<>();
        performanceQueryRepository.findDailyConfirmedPurchases(
                        selectorsId, period.startInclusive(), period.endExclusive())
                .forEach(row -> purchasesByDay.put(row.dayOfMonth(), row));

        int lastDay = activityMonth.equals(YearMonth.now(clock))
                ? LocalDate.now(clock).getDayOfMonth()
                : activityMonth.lengthOfMonth();
        List<PerformanceTrendResponse> trends = new ArrayList<>(lastDay);
        for (int day = 1; day <= lastDay; day++) {
            DailyClick click = clicksByDay.get(day);
            DailyPurchase purchase = purchasesByDay.get(day);
            trends.add(new PerformanceTrendResponse(
                    activityMonth.atDay(day),
                    click == null ? 0L : click.clickCount(),
                    purchase == null ? 0L : purchase.conversionCount(),
                    purchase == null ? 0L : requireWholeWon(purchase.conversionAmount())));
        }
        return trends;
    }

    private List<ProductPerformanceResponse> getProducts(
            Long selectorsId, YearMonth activityMonth, BigDecimal settlementRate) {
        Period period = Period.of(activityMonth);
        Map<Long, ProductStats> products = new HashMap<>();
        for (ProductClick click : performanceQueryRepository.findProductClicks(
                selectorsId, period.startInclusive(), period.endExclusive())) {
            products.computeIfAbsent(click.productId(), ignored -> ProductStats.from(click))
                    .clickCount = click.clickCount();
        }
        for (ProductPurchase purchase : performanceQueryRepository.findProductConfirmedPurchases(
                selectorsId, period.startInclusive(), period.endExclusive())) {
            ProductStats stats = products.computeIfAbsent(
                    purchase.productId(), ignored -> ProductStats.from(purchase));
            stats.conversionCount = purchase.conversionCount();
            stats.conversionAmount = requireWholeWon(purchase.conversionAmount());
        }

        return products.values().stream()
                .map(stats -> stats.toResponse(settlementRate))
                .sorted(Comparator.comparingLong(ProductPerformanceResponse::conversionCount)
                        .reversed()
                        .thenComparing(Comparator.comparingLong(
                                ProductPerformanceResponse::conversionAmount).reversed())
                        .thenComparing(Comparator.comparingLong(
                                ProductPerformanceResponse::clickCount).reversed())
                        .thenComparing(ProductPerformanceResponse::productId))
                .toList();
    }

    private YearMonth resolveActivityMonth(YearMonth requestedMonth) {
        YearMonth currentMonth = YearMonth.now(clock);
        YearMonth resolved = requestedMonth == null ? currentMonth : requestedMonth;
        if (resolved.isAfter(currentMonth)) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT, "현재 월 이후의 성과는 조회할 수 없습니다.");
        }
        return resolved;
    }

    private BigDecimal getSettlementRate(Selectors selectors) {
        if (selectors.getApplicationId() == null) {
            throw new BusinessException(ErrorCode.SETTLEMENT_RATE_SOURCE_NOT_FOUND);
        }
        Application application = applicationRepository.findById(selectors.getApplicationId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.SETTLEMENT_RATE_SOURCE_NOT_FOUND));
        return commissionRateCalculator.calculate(
                application.getSnsCode(), application.getFollowerCount());
    }

    private BigDecimal calculateConversionRate(long conversionCount, long clickCount) {
        if (clickCount == 0L) {
            return new BigDecimal("0.00");
        }
        return BigDecimal.valueOf(conversionCount)
                .multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(clickCount), 2, RoundingMode.HALF_UP);
    }

    private long calculateSettlementAmount(long conversionAmount, BigDecimal settlementRate) {
        return BigDecimal.valueOf(conversionAmount)
                .multiply(settlementRate)
                .divide(ONE_HUNDRED, 0, RoundingMode.FLOOR)
                .longValueExact();
    }

    private long requireWholeWon(BigDecimal amount) {
        try {
            return amount.longValueExact();
        } catch (ArithmeticException e) {
            throw new BusinessException(ErrorCode.INVALID_SETTLEMENT_AMOUNT);
        }
    }

    private record Period(LocalDateTime startInclusive, LocalDateTime endExclusive) {

        private static Period of(YearMonth month) {
            return new Period(
                    month.atDay(1).atStartOfDay(),
                    month.plusMonths(1).atDay(1).atStartOfDay());
        }
    }

    private static final class ProductStats {

        private final Long productId;
        private final String productCode;
        private final String productName;
        private final String brandName;
        private final String thumbnailUrl;
        private final String detailUrl;
        private long clickCount;
        private long conversionCount;
        private long conversionAmount;

        private ProductStats(
                Long productId,
                String productCode,
                String productName,
                String brandName,
                String thumbnailUrl,
                String detailUrl) {
            this.productId = productId;
            this.productCode = productCode;
            this.productName = productName;
            this.brandName = brandName;
            this.thumbnailUrl = thumbnailUrl;
            this.detailUrl = detailUrl;
        }

        private static ProductStats from(ProductClick row) {
            return new ProductStats(
                    row.productId(), row.productCode(), row.productName(),
                    row.brandName(), row.thumbnailUrl(), row.detailUrl());
        }

        private static ProductStats from(ProductPurchase row) {
            return new ProductStats(
                    row.productId(), row.productCode(), row.productName(),
                    row.brandName(), row.thumbnailUrl(), row.detailUrl());
        }

        private ProductPerformanceResponse toResponse(BigDecimal settlementRate) {
            return new ProductPerformanceResponse(
                    productId,
                    productCode,
                    productName,
                    brandName,
                    thumbnailUrl,
                    detailUrl,
                    clickCount,
                    conversionCount,
                    conversionAmount,
                    clickCount == 0L
                            ? new BigDecimal("0.00")
                            : BigDecimal.valueOf(conversionCount)
                                    .multiply(ONE_HUNDRED)
                                    .divide(BigDecimal.valueOf(clickCount), 2, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(conversionAmount)
                            .multiply(settlementRate)
                            .divide(ONE_HUNDRED, 0, RoundingMode.FLOOR)
                            .longValueExact());
        }
    }
}
