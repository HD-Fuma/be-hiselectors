package com.fuma.hiselectors.performance.repository;

import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CampaignPerformanceQueryRepository {

    private static final EnumSet<PurchaseStatus> TERMINAL_STATUSES = EnumSet.of(
            PurchaseStatus.PURCHASE_CONFIRMED,
            PurchaseStatus.CANCELED,
            PurchaseStatus.RETURNED);

    private final EntityManager entityManager;

    /**
     * 현재 캠페인 상품 그룹에 귀속할 수 있는 구매 행을 반환한다.
     *
     * <p>한 상품이 같은 캠페인의 여러 그룹에 들어 있어도 EXISTS를 사용하므로 구매 행은
     * 중복되지 않는다. 그룹과 상품이 구매 전에 만들어진 경우만 귀속하며, 삭제된
     * 셀렉터스와 셀렉터스 본인의 구매는 성과에서 제외한다.</p>
     */
    public List<AttributedPurchase> findAttributedTerminalPurchases(
            Long campaignId,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive) {
        return entityManager.createQuery("""
                        select ph.id, ph.orderNo, ph.selectorsId,
                               s.selectorsCode, s.selectorsNickname,
                               account.profileImageUrl,
                               ph.productId, p.productCode, p.productName,
                               p.brandName, p.thumbnailUrl,
                               ph.quantity, ph.paidAmount, ph.status, ph.purchasedAt
                        from PurchaseHistory ph
                        join Product p on p.id = ph.productId
                        join Selectors s on s.id = ph.selectorsId
                        left join SelectorsSnsAccount account
                          on account.selectorsId = s.id and account.deleted = false
                        where ph.selectorsId is not null
                          and s.deleted = false
                          and (s.userId is null or ph.userId <> s.userId)
                          and ph.status in :terminalStatuses
                          and (ph.status <> :confirmedStatus or ph.confirmedAt is not null)
                          and ph.purchasedAt >= :startInclusive
                          and ph.purchasedAt < :endExclusive
                          and exists (
                              select item.id
                              from ProductGroupItem item
                              where item.group.campaignId = :campaignId
                                and item.group.selectorsId = ph.selectorsId
                                and item.product.id = ph.productId
                                and item.group.deleted = false
                                and item.deleted = false
                                and item.group.createdAt <= ph.purchasedAt
                                and item.createdAt <= ph.purchasedAt
                          )
                        order by ph.purchasedAt, ph.id
                        """, Object[].class)
                .setParameter("campaignId", campaignId)
                .setParameter("terminalStatuses", TERMINAL_STATUSES)
                .setParameter("confirmedStatus", PurchaseStatus.PURCHASE_CONFIRMED)
                .setParameter("startInclusive", startInclusive)
                .setParameter("endExclusive", endExclusive)
                .getResultList().stream()
                .map(AttributedPurchase::from)
                .toList();
    }

    public record AttributedPurchase(
            Long purchaseId,
            String orderNo,
            Long selectorId,
            String selectorCode,
            String selectorNickname,
            String selectorProfileImageUrl,
            Long productId,
            String productCode,
            String productName,
            String brandName,
            String thumbnailUrl,
            int quantity,
            BigDecimal paidAmount,
            PurchaseStatus status,
            LocalDateTime purchasedAt
    ) {

        private static AttributedPurchase from(Object[] row) {
            return new AttributedPurchase(
                    (Long) row[0],
                    (String) row[1],
                    (Long) row[2],
                    (String) row[3],
                    (String) row[4],
                    (String) row[5],
                    (Long) row[6],
                    (String) row[7],
                    (String) row[8],
                    (String) row[9],
                    (String) row[10],
                    ((Number) row[11]).intValue(),
                    (BigDecimal) row[12],
                    (PurchaseStatus) row[13],
                    (LocalDateTime) row[14]);
        }
    }
}
