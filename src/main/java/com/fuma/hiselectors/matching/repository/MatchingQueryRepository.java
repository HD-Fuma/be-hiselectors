package com.fuma.hiselectors.matching.repository;

import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.selectors.model.Selectors;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 신규 상품·캠페인에 적합한 셀렉터스를 카테고리별 확정 매출로 뽑기 위한 조회. */
@Repository
@RequiredArgsConstructor
public class MatchingQueryRepository {

    private final EntityManager entityManager;

    /**
     * 주어진 카테고리(들) 상품의 확정 매출을 셀렉터스별로 집계한다(매출 내림차순).
     * 삭제·블랙리스트 셀렉터스와 본인 구매는 제외한다.
     */
    public List<CategorySales> summarizeCategoryConfirmedSales(
            Collection<String> categories,
            LocalDateTime startInclusive, LocalDateTime endExclusive) {
        if (categories.isEmpty()) {
            return List.of();
        }
        StringBuilder jpql = new StringBuilder("""
                select p.selectorsId, coalesce(sum(p.paidAmount), 0), count(distinct p.orderNo)
                from PurchaseHistory p
                join Selectors s on s.id = p.selectorsId
                join Product prod on prod.id = p.productId
                where s.deleted = false
                  and s.selectorsRoleId <> :blacklist
                  and prod.category in :categories
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
        jpql.append(" group by p.selectorsId order by sum(p.paidAmount) desc");

        TypedQuery<Object[]> query = entityManager.createQuery(jpql.toString(), Object[].class)
                .setParameter("categories", categories)
                .setParameter("blacklist", Selectors.BLACKLIST_ROLE)
                .setParameter("status", PurchaseStatus.PURCHASE_CONFIRMED);
        if (startInclusive != null) {
            query.setParameter("startInclusive", startInclusive);
        }
        if (endExclusive != null) {
            query.setParameter("endExclusive", endExclusive);
        }
        return query.getResultList().stream()
                .map(row -> new CategorySales(
                        (Long) row[0], (BigDecimal) row[1], ((Number) row[2]).longValue()))
                .toList();
    }

    /** 대표 카테고리가 일치하는(아직 실적이 없어도) 셀렉터스. 신규 카테고리 커버용. */
    public List<Selectors> findRepresentativeCategorySelectors(Collection<String> categories) {
        if (categories.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                        select s
                        from Selectors s
                        where s.deleted = false
                          and s.selectorsRoleId <> :blacklist
                          and s.category in :categories
                        order by s.id
                        """, Selectors.class)
                .setParameter("categories", categories)
                .setParameter("blacklist", Selectors.BLACKLIST_ROLE)
                .getResultList();
    }

    public record CategorySales(
            Long selectorId,
            BigDecimal totalSales,
            long confirmedOrderCount
    ) {
    }
}
