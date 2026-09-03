package com.fuma.hiselectors.performance.repository;

import com.fuma.hiselectors.analytics.model.ViewPageType;
import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PerformanceQueryRepository {

    private static final List<PurchaseStatus> PERFORMANCE_PURCHASE_STATUSES = List.of(
            PurchaseStatus.PURCHASED,
            PurchaseStatus.PURCHASE_CONFIRMED);

    private final EntityManager entityManager;

    public long countProductClicks(
            Long selectorsId, LocalDateTime startInclusive, LocalDateTime endExclusive) {
        return entityManager.createQuery("""
                        select count(c)
                        from ClickLog c
                        where c.selectorsId = :selectorsId
                          and c.linkType = :linkType
                          and c.createdAt >= :startInclusive
                          and c.createdAt < :endExclusive
                        """, Long.class)
                .setParameter("selectorsId", selectorsId)
                .setParameter("linkType", ViewPageType.PRODUCT)
                .setParameter("startInclusive", startInclusive)
                .setParameter("endExclusive", endExclusive)
                .getSingleResult();
    }

    public PurchaseSummary summarizePerformancePurchases(
            Long selectorsId, LocalDateTime startInclusive, LocalDateTime endExclusive) {
        Object[] row = entityManager.createQuery("""
                        select coalesce(sum(p.paidAmount), 0), count(p)
                        from PurchaseHistory p
                        where p.selectorsId = :selectorsId
                          and p.status in :statuses
                          and p.purchasedAt >= :startInclusive
                          and p.purchasedAt < :endExclusive
                        """, Object[].class)
                .setParameter("selectorsId", selectorsId)
                .setParameter("statuses", PERFORMANCE_PURCHASE_STATUSES)
                .setParameter("startInclusive", startInclusive)
                .setParameter("endExclusive", endExclusive)
                .getSingleResult();
        return new PurchaseSummary((BigDecimal) row[0], ((Number) row[1]).longValue());
    }

    public List<DailyClick> findDailyProductClicks(
            Long selectorsId, LocalDateTime startInclusive, LocalDateTime endExclusive) {
        return entityManager.createQuery("""
                        select day(c.createdAt), count(c)
                        from ClickLog c
                        where c.selectorsId = :selectorsId
                          and c.linkType = :linkType
                          and c.createdAt >= :startInclusive
                          and c.createdAt < :endExclusive
                        group by day(c.createdAt)
                        order by day(c.createdAt)
                        """, Object[].class)
                .setParameter("selectorsId", selectorsId)
                .setParameter("linkType", ViewPageType.PRODUCT)
                .setParameter("startInclusive", startInclusive)
                .setParameter("endExclusive", endExclusive)
                .getResultList().stream()
                .map(row -> new DailyClick(
                        ((Number) row[0]).intValue(), ((Number) row[1]).longValue()))
                .toList();
    }

    public List<DailyPurchase> findDailyPerformancePurchases(
            Long selectorsId, LocalDateTime startInclusive, LocalDateTime endExclusive) {
        return entityManager.createQuery("""
                        select day(p.purchasedAt), count(p), coalesce(sum(p.paidAmount), 0)
                        from PurchaseHistory p
                        where p.selectorsId = :selectorsId
                          and p.status in :statuses
                          and p.purchasedAt >= :startInclusive
                          and p.purchasedAt < :endExclusive
                        group by day(p.purchasedAt)
                        order by day(p.purchasedAt)
                        """, Object[].class)
                .setParameter("selectorsId", selectorsId)
                .setParameter("statuses", PERFORMANCE_PURCHASE_STATUSES)
                .setParameter("startInclusive", startInclusive)
                .setParameter("endExclusive", endExclusive)
                .getResultList().stream()
                .map(row -> new DailyPurchase(
                        ((Number) row[0]).intValue(),
                        ((Number) row[1]).longValue(),
                        (BigDecimal) row[2]))
                .toList();
    }

    public List<ProductClick> findProductClicks(
            Long selectorsId, LocalDateTime startInclusive, LocalDateTime endExclusive) {
        return entityManager.createQuery("""
                        select product.id, product.productCode, product.productName,
                               product.brandName, product.thumbnailUrl, product.detailUrl, count(c)
                        from ClickLog c
                        join Product product on product.id = c.referenceId
                        where c.selectorsId = :selectorsId
                          and c.linkType = :linkType
                          and c.createdAt >= :startInclusive
                          and c.createdAt < :endExclusive
                        group by product.id, product.productCode, product.productName,
                                 product.brandName, product.thumbnailUrl, product.detailUrl
                        """, Object[].class)
                .setParameter("selectorsId", selectorsId)
                .setParameter("linkType", ViewPageType.PRODUCT)
                .setParameter("startInclusive", startInclusive)
                .setParameter("endExclusive", endExclusive)
                .getResultList().stream()
                .map(row -> new ProductClick(
                        (Long) row[0], (String) row[1], (String) row[2],
                        (String) row[3], (String) row[4], (String) row[5],
                        ((Number) row[6]).longValue()))
                .toList();
    }

    public List<ProductPurchase> findProductPerformancePurchases(
            Long selectorsId, LocalDateTime startInclusive, LocalDateTime endExclusive) {
        return entityManager.createQuery("""
                        select product.id, product.productCode, product.productName,
                               product.brandName, product.thumbnailUrl, product.detailUrl,
                               count(p), coalesce(sum(p.paidAmount), 0)
                        from PurchaseHistory p
                        join Product product on product.id = p.productId
                        where p.selectorsId = :selectorsId
                          and p.status in :statuses
                          and p.purchasedAt >= :startInclusive
                          and p.purchasedAt < :endExclusive
                        group by product.id, product.productCode, product.productName,
                                 product.brandName, product.thumbnailUrl, product.detailUrl
                        """, Object[].class)
                .setParameter("selectorsId", selectorsId)
                .setParameter("statuses", PERFORMANCE_PURCHASE_STATUSES)
                .setParameter("startInclusive", startInclusive)
                .setParameter("endExclusive", endExclusive)
                .getResultList().stream()
                .map(row -> new ProductPurchase(
                        (Long) row[0], (String) row[1], (String) row[2],
                        (String) row[3], (String) row[4], (String) row[5],
                        ((Number) row[6]).longValue(), (BigDecimal) row[7]))
                .toList();
    }

    public record PurchaseSummary(BigDecimal conversionAmount, long conversionCount) {
    }

    public record DailyClick(int dayOfMonth, long clickCount) {
    }

    public record DailyPurchase(
            int dayOfMonth, long conversionCount, BigDecimal conversionAmount) {
    }

    public record ProductClick(
            Long productId,
            String productCode,
            String productName,
            String brandName,
            String thumbnailUrl,
            String detailUrl,
            long clickCount
    ) {
    }

    public record ProductPurchase(
            Long productId,
            String productCode,
            String productName,
            String brandName,
            String thumbnailUrl,
            String detailUrl,
            long conversionCount,
            BigDecimal conversionAmount
    ) {
    }
}
