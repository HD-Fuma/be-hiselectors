package com.fuma.hiselectors.performance.service;

import com.fuma.hiselectors.campaign.model.Campaign;
import com.fuma.hiselectors.campaign.repository.CampaignRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.performance.dto.CampaignPerformanceResponse;
import com.fuma.hiselectors.performance.dto.CampaignPerformanceResponse.DailyPerformance;
import com.fuma.hiselectors.performance.dto.CampaignPerformanceResponse.ProductPerformance;
import com.fuma.hiselectors.performance.dto.CampaignPerformanceResponse.SelectorPerformance;
import com.fuma.hiselectors.performance.dto.CampaignPerformanceResponse.Summary;
import com.fuma.hiselectors.performance.repository.CampaignPerformanceQueryRepository;
import com.fuma.hiselectors.performance.repository.CampaignPerformanceQueryRepository.AttributedPurchase;
import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampaignPerformanceAdminService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final CampaignRepository campaignRepository;
    private final CampaignPerformanceQueryRepository queryRepository;

    public CampaignPerformanceResponse getPerformance(
            Long campaignId, LocalDate startDate, LocalDate endDate) {
        validateRequestedPeriod(startDate, endDate);
        Campaign campaign = campaignRepository.findByIdAndIsDeletedFalse(campaignId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CAMPAIGN_NOT_FOUND));
        Period period = Period.clampedTo(campaign, startDate, endDate);
        List<AttributedPurchase> purchases = queryRepository.findAttributedTerminalPurchases(
                campaignId, period.startInclusive(), period.endExclusive());

        return aggregate(campaignId, period, purchases);
    }

    private CampaignPerformanceResponse aggregate(
            Long campaignId, Period period, List<AttributedPurchase> purchases) {
        MutableAggregate total = new MutableAggregate();
        Map<LocalDate, MutableAggregate> daily = emptyDaily(period);
        Map<Long, MutableProductAggregate> products = new LinkedHashMap<>();
        Map<Long, MutableSelectorAggregate> selectors = new LinkedHashMap<>();
        Set<String> finalizedOrders = new HashSet<>();
        Set<String> canceledOrReturnedOrders = new HashSet<>();

        for (AttributedPurchase purchase : purchases) {
            finalizedOrders.add(purchase.orderNo());
            if (isCanceledOrReturned(purchase.status())) {
                canceledOrReturnedOrders.add(purchase.orderNo());
                continue;
            }
            if (purchase.status() != PurchaseStatus.PURCHASE_CONFIRMED) {
                continue;
            }

            total.add(purchase);
            daily.get(purchase.purchasedAt().toLocalDate()).add(purchase);
            products.computeIfAbsent(
                            purchase.productId(), ignored -> new MutableProductAggregate(purchase))
                    .add(purchase);
            selectors.computeIfAbsent(
                            purchase.selectorId(), ignored -> new MutableSelectorAggregate(purchase))
                    .add(purchase);
        }

        Summary summary = new Summary(
                total.sales,
                total.orderNos.size(),
                total.quantity,
                total.selectorIds.size(),
                canceledOrReturnedOrders.size(),
                rate(canceledOrReturnedOrders.size(), finalizedOrders.size()));

        return new CampaignPerformanceResponse(
                campaignId,
                period.startDate(),
                period.endDate(),
                summary,
                toDaily(daily),
                toProducts(products),
                toSelectors(selectors));
    }

    private Map<LocalDate, MutableAggregate> emptyDaily(Period period) {
        Map<LocalDate, MutableAggregate> result = new LinkedHashMap<>();
        for (LocalDate date = period.startDate();
                !date.isAfter(period.endDate());
                date = date.plusDays(1)) {
            result.put(date, new MutableAggregate());
        }
        return result;
    }

    private List<DailyPerformance> toDaily(Map<LocalDate, MutableAggregate> daily) {
        return daily.entrySet().stream()
                .map(entry -> new DailyPerformance(
                        entry.getKey(),
                        entry.getValue().sales,
                        entry.getValue().orderNos.size(),
                        entry.getValue().quantity))
                .toList();
    }

    private List<ProductPerformance> toProducts(
            Map<Long, MutableProductAggregate> products) {
        List<ProductPerformance> result = new ArrayList<>();
        for (MutableProductAggregate product : products.values()) {
            result.add(new ProductPerformance(
                    product.productId,
                    product.productCode,
                    product.productName,
                    product.brandName,
                    product.thumbnailUrl,
                    product.aggregate.sales,
                    product.aggregate.orderNos.size(),
                    product.aggregate.quantity,
                    product.aggregate.selectorIds.size()));
        }
        result.sort(Comparator.comparing(
                        ProductPerformance::confirmedSales, Comparator.reverseOrder())
                .thenComparing(
                        ProductPerformance::confirmedOrderCount, Comparator.reverseOrder())
                .thenComparing(ProductPerformance::productId));
        return List.copyOf(result);
    }

    private List<SelectorPerformance> toSelectors(
            Map<Long, MutableSelectorAggregate> selectors) {
        List<SelectorPerformance> result = new ArrayList<>();
        for (MutableSelectorAggregate selector : selectors.values()) {
            result.add(new SelectorPerformance(
                    selector.selectorId,
                    selector.selectorCode,
                    selector.selectorNickname,
                    selector.selectorProfileImageUrl,
                    selector.aggregate.sales,
                    selector.aggregate.orderNos.size(),
                    selector.aggregate.quantity,
                    selector.productIds.size()));
        }
        result.sort(Comparator.comparing(
                        SelectorPerformance::confirmedSales, Comparator.reverseOrder())
                .thenComparing(
                        SelectorPerformance::confirmedOrderCount, Comparator.reverseOrder())
                .thenComparing(SelectorPerformance::selectorId));
        return List.copyOf(result);
    }

    private boolean isCanceledOrReturned(PurchaseStatus status) {
        return status == PurchaseStatus.CANCELED || status == PurchaseStatus.RETURNED;
    }

    private BigDecimal rate(long numerator, long denominator) {
        if (denominator == 0L) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private void validateRequestedPeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT, "시작일은 종료일보다 늦을 수 없습니다.");
        }
    }

    private static final class MutableAggregate {

        private BigDecimal sales = BigDecimal.ZERO;
        private long quantity;
        private final Set<String> orderNos = new HashSet<>();
        private final Set<Long> selectorIds = new HashSet<>();

        private void add(AttributedPurchase purchase) {
            sales = sales.add(purchase.paidAmount());
            quantity += purchase.quantity();
            orderNos.add(purchase.orderNo());
            selectorIds.add(purchase.selectorId());
        }
    }

    private static final class MutableProductAggregate {

        private final Long productId;
        private final String productCode;
        private final String productName;
        private final String brandName;
        private final String thumbnailUrl;
        private final MutableAggregate aggregate = new MutableAggregate();

        private MutableProductAggregate(AttributedPurchase purchase) {
            productId = purchase.productId();
            productCode = purchase.productCode();
            productName = purchase.productName();
            brandName = purchase.brandName();
            thumbnailUrl = purchase.thumbnailUrl();
        }

        private void add(AttributedPurchase purchase) {
            aggregate.add(purchase);
        }
    }

    private static final class MutableSelectorAggregate {

        private final Long selectorId;
        private final String selectorCode;
        private final String selectorNickname;
        private final String selectorProfileImageUrl;
        private final MutableAggregate aggregate = new MutableAggregate();
        private final Set<Long> productIds = new HashSet<>();

        private MutableSelectorAggregate(AttributedPurchase purchase) {
            selectorId = purchase.selectorId();
            selectorCode = purchase.selectorCode();
            selectorNickname = purchase.selectorNickname();
            selectorProfileImageUrl = purchase.selectorProfileImageUrl();
        }

        private void add(AttributedPurchase purchase) {
            aggregate.add(purchase);
            productIds.add(purchase.productId());
        }
    }

    private record Period(
            LocalDate startDate,
            LocalDate endDate,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive
    ) {

        private static Period clampedTo(
                Campaign campaign, LocalDate requestedStart, LocalDate requestedEnd) {
            LocalDate start = requestedStart == null
                    ? campaign.getStartDate()
                    : laterOf(campaign.getStartDate(), requestedStart);
            LocalDate end = requestedEnd == null
                    ? campaign.getEndDate()
                    : earlierOf(campaign.getEndDate(), requestedEnd);
            if (start.isAfter(end)) {
                throw new BusinessException(
                        ErrorCode.INVALID_INPUT, "조회 기간이 캠페인 기간과 겹치지 않습니다.");
            }
            try {
                return new Period(
                        start, end, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
            } catch (DateTimeException exception) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "조회 기간이 올바르지 않습니다.");
            }
        }

        private static LocalDate laterOf(LocalDate first, LocalDate second) {
            return first.isAfter(second) ? first : second;
        }

        private static LocalDate earlierOf(LocalDate first, LocalDate second) {
            return first.isBefore(second) ? first : second;
        }
    }
}
