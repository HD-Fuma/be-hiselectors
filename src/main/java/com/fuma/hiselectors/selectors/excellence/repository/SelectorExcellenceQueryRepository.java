package com.fuma.hiselectors.selectors.excellence.repository;

import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.selectors.model.Selectors;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 우수 활동자 선정 시점의 기수별 확정 매출을 집계한다. */
@Repository
@RequiredArgsConstructor
public class SelectorExcellenceQueryRepository {

    private final EntityManager entityManager;

    public List<SalesCandidate> findSalesCandidates(
            Long generationId,
            LocalDateTime activityStartInclusive,
            LocalDateTime activityEndExclusive,
            LocalDateTime confirmedAsOf) {
        return entityManager.createQuery("""
                        select s.id,
                               coalesce(sum(p.paidAmount), 0),
                               count(distinct p.orderNo)
                        from SelectorsGeneration membership
                        join Selectors s on s.id = membership.selectorsId
                        join PurchaseHistory p on p.selectorsId = s.id
                        where membership.generationId = :generationId
                          and s.deleted = false
                          and upper(s.selectorsRoleId) <> :blacklistRole
                          and p.status = :confirmedStatus
                          and p.confirmedAt is not null
                          and p.confirmedAt <= :confirmedAsOf
                          and p.purchasedAt >= :activityStartInclusive
                          and p.purchasedAt < :activityEndExclusive
                          and p.purchasedAt >= coalesce(
                                  membership.createdAt, :activityStartInclusive)
                          and (s.userId is null or p.userId <> s.userId)
                        group by s.id
                        having count(distinct p.orderNo) > 0
                           and coalesce(sum(p.paidAmount), 0) > 0
                        order by sum(p.paidAmount) desc, s.id asc
                        """, Object[].class)
                .setParameter("generationId", generationId)
                .setParameter("blacklistRole", Selectors.BLACKLIST_ROLE)
                .setParameter("confirmedStatus", PurchaseStatus.PURCHASE_CONFIRMED)
                .setParameter("confirmedAsOf", confirmedAsOf)
                .setParameter("activityStartInclusive", activityStartInclusive)
                .setParameter("activityEndExclusive", activityEndExclusive)
                .getResultList().stream()
                .map(row -> new SalesCandidate(
                        (Long) row[0],
                        (BigDecimal) row[1],
                        ((Number) row[2]).longValue()))
                .toList();
    }

    /**
     * 활동 기간 구매 중 아직 자동확정되지 않은 건이 남아 있는지 확인한다.
     *
     * <p>선정 완료 마커를 먼저 기록하면 자동확정 배치 장애 때 누락된 매출을 다시
     * 반영할 수 없으므로, 미확정 구매가 하나라도 있으면 해당 기수 선정을 다음 실행으로 미룬다.
     */
    public boolean hasPendingPurchases(
            Long generationId,
            LocalDateTime activityStartInclusive,
            LocalDateTime activityEndExclusive) {
        Long count = entityManager.createQuery("""
                        select count(p.id)
                        from SelectorsGeneration membership
                        join Selectors s on s.id = membership.selectorsId
                        join PurchaseHistory p on p.selectorsId = s.id
                        where membership.generationId = :generationId
                          and s.deleted = false
                          and upper(s.selectorsRoleId) <> :blacklistRole
                          and p.status = :pendingStatus
                          and p.purchasedAt >= :activityStartInclusive
                          and p.purchasedAt < :activityEndExclusive
                          and p.purchasedAt >= coalesce(
                                  membership.createdAt, :activityStartInclusive)
                          and (s.userId is null or p.userId <> s.userId)
                        """, Long.class)
                .setParameter("generationId", generationId)
                .setParameter("blacklistRole", Selectors.BLACKLIST_ROLE)
                .setParameter("pendingStatus", PurchaseStatus.PURCHASED)
                .setParameter("activityStartInclusive", activityStartInclusive)
                .setParameter("activityEndExclusive", activityEndExclusive)
                .getSingleResult();
        return count > 0;
    }

    public record SalesCandidate(
            Long selectorsId,
            BigDecimal generationSales,
            long confirmedOrderCount
    ) {
    }
}
