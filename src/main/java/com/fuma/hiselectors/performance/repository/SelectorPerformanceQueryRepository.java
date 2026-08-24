package com.fuma.hiselectors.performance.repository;

import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.selectors.model.Selectors;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SelectorPerformanceQueryRepository {

    private final EntityManager entityManager;

    public List<Selectors> findAllVisibleSelectors() {
        return entityManager.createQuery("""
                        select s
                        from Selectors s
                        where s.deleted = false
                        order by s.id
                        """, Selectors.class)
                .getResultList();
    }

    /**
     * 셀렉터스별 참여 기수를 최신 활동 시작일, 기수 ID 역순으로 반환한다.
     * 서비스는 각 셀렉터스의 첫 행을 최신 기수로 사용한다.
     */
    public List<GenerationMembership> findGenerationMemberships(List<Long> selectorIds) {
        if (selectorIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                        select sg.selectorsId, g.id, g.generationName
                        from SelectorsGeneration sg
                        join Generation g on g.id = sg.generationId
                        where sg.selectorsId in :selectorIds
                        order by sg.selectorsId, g.activityStartDate desc, g.id desc
                        """, Object[].class)
                .setParameter("selectorIds", selectorIds)
                .getResultList().stream()
                .map(row -> new GenerationMembership(
                        (Long) row[0], (Long) row[1], (String) row[2]))
                .toList();
    }

    /**
     * 확정 시각 기준 매출을 집계한다. 셀렉터스 본인이 구매한 주문은 성과에서 제외한다.
     */
    public List<ConfirmedSales> summarizeConfirmedSales(
            List<Long> selectorIds,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive) {
        if (selectorIds.isEmpty()) {
            return List.of();
        }

        StringBuilder jpql = new StringBuilder("""
                select p.selectorsId, coalesce(sum(p.paidAmount), 0), count(distinct p.orderNo)
                from PurchaseHistory p
                join Selectors s on s.id = p.selectorsId
                where p.selectorsId in :selectorIds
                  and s.deleted = false
                  and p.status = :status
                  and p.confirmedAt is not null
                  and (s.userId is null or p.userId <> s.userId)
                """);
        if (startInclusive != null) {
            jpql.append(" and p.confirmedAt >= :startInclusive");
        }
        if (endExclusive != null) {
            jpql.append(" and p.confirmedAt < :endExclusive");
        }
        jpql.append(" group by p.selectorsId");

        var query = entityManager.createQuery(jpql.toString(), Object[].class)
                .setParameter("selectorIds", selectorIds)
                .setParameter("status", PurchaseStatus.PURCHASE_CONFIRMED);
        if (startInclusive != null) {
            query.setParameter("startInclusive", startInclusive);
        }
        if (endExclusive != null) {
            query.setParameter("endExclusive", endExclusive);
        }

        return query.getResultList().stream()
                .map(row -> new ConfirmedSales(
                        (Long) row[0],
                        (BigDecimal) row[1],
                        ((Number) row[2]).longValue()))
                .toList();
    }

    public record GenerationMembership(
            Long selectorId,
            Long generationId,
            String generationName
    ) {
    }

    public record ConfirmedSales(
            Long selectorId,
            BigDecimal totalSales,
            long confirmedOrderCount
    ) {
        public static final ConfirmedSales ZERO =
                new ConfirmedSales(null, BigDecimal.ZERO, 0L);
    }
}
