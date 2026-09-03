package com.fuma.hiselectors.performance.repository;

import com.fuma.hiselectors.analytics.model.ViewPageType;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.selectors.model.Selectors;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
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

    public List<Selectors> findVisibleMembers(List<Long> generationIds) {
        if (generationIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                        select distinct s
                        from Selectors s
                        join SelectorsGeneration sg on sg.selectorsId = s.id
                        where s.deleted = false
                          and sg.generationId in :generationIds
                        order by s.id
                        """, Selectors.class)
                .setParameter("generationIds", generationIds)
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
        appendConfirmedAtPeriod(jpql, startInclusive, endExclusive);
        jpql.append(" group by p.selectorsId");

        return confirmedSalesQuery(jpql.toString(), selectorIds, startInclusive, endExclusive)
                .getResultList().stream()
                .map(row -> new ConfirmedSales(
                        (Long) row[0],
                        (BigDecimal) row[1],
                        ((Number) row[2]).longValue()))
                .toList();
    }

    /** 현재 기간과 직전 기간의 확정 매출을 한 번의 조회로 집계한다. */
    public List<ConfirmedSalesComparison> summarizeConfirmedSalesComparison(
            List<Long> selectorIds,
            LocalDateTime currentStartInclusive,
            LocalDateTime currentEndExclusive,
            LocalDateTime previousStartInclusive,
            LocalDateTime previousEndExclusive) {
        if (selectorIds.isEmpty()) {
            return List.of();
        }

        return entityManager.createQuery("""
                        select p.selectorsId,
                               coalesce(sum(case
                                   when p.confirmedAt >= :currentStartInclusive
                                    and p.confirmedAt < :currentEndExclusive
                                   then p.paidAmount else 0 end), 0),
                               count(distinct case
                                   when p.confirmedAt >= :currentStartInclusive
                                    and p.confirmedAt < :currentEndExclusive
                                   then p.orderNo else null end),
                               coalesce(sum(case
                                   when p.confirmedAt >= :previousStartInclusive
                                    and p.confirmedAt < :previousEndExclusive
                                   then p.paidAmount else 0 end), 0),
                               count(distinct case
                                   when p.confirmedAt >= :previousStartInclusive
                                    and p.confirmedAt < :previousEndExclusive
                                   then p.orderNo else null end)
                        from PurchaseHistory p
                        join Selectors s on s.id = p.selectorsId
                        where p.selectorsId in :selectorIds
                          and s.deleted = false
                          and p.status = :status
                          and p.confirmedAt >= :previousStartInclusive
                          and p.confirmedAt < :currentEndExclusive
                          and (s.userId is null or p.userId <> s.userId)
                        group by p.selectorsId
                        """, Object[].class)
                .setParameter("selectorIds", selectorIds)
                .setParameter("status", PurchaseStatus.PURCHASE_CONFIRMED)
                .setParameter("currentStartInclusive", currentStartInclusive)
                .setParameter("currentEndExclusive", currentEndExclusive)
                .setParameter("previousStartInclusive", previousStartInclusive)
                .setParameter("previousEndExclusive", previousEndExclusive)
                .getResultList().stream()
                .map(row -> new ConfirmedSalesComparison(
                        (Long) row[0],
                        (BigDecimal) row[1],
                        ((Number) row[2]).longValue(),
                        (BigDecimal) row[3],
                        ((Number) row[4]).longValue()))
                .toList();
    }

    public List<DatedSales> summarizeConfirmedSalesByDay(
            List<Long> selectorIds,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive) {
        return summarizeConfirmedSalesByDatePart(selectorIds, startInclusive, endExclusive, true);
    }

    /** 대시보드의 일별 매출·예상 정산액 계산을 위해 셀렉터스와 날짜별 매출만 집계한다. */
    public List<DatedSelectorSales> summarizeConfirmedSalesBySelectorAndDay(
            List<Long> selectorIds,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive) {
        if (selectorIds.isEmpty()) {
            return List.of();
        }
        StringBuilder jpql = new StringBuilder("""
                select p.selectorsId,
                       year(p.confirmedAt), month(p.confirmedAt), day(p.confirmedAt),
                       coalesce(sum(p.paidAmount), 0)
                from PurchaseHistory p
                join Selectors s on s.id = p.selectorsId
                where p.selectorsId in :selectorIds
                  and s.deleted = false
                  and p.status = :status
                  and p.confirmedAt is not null
                  and (s.userId is null or p.userId <> s.userId)
                """);
        appendConfirmedAtPeriod(jpql, startInclusive, endExclusive);
        jpql.append(" group by p.selectorsId, year(p.confirmedAt),"
                + " month(p.confirmedAt), day(p.confirmedAt)");
        return confirmedSalesQuery(jpql.toString(), selectorIds, startInclusive, endExclusive)
                .getResultList().stream()
                .map(row -> new DatedSelectorSales(
                        (Long) row[0],
                        LocalDate.of(
                                ((Number) row[1]).intValue(),
                                ((Number) row[2]).intValue(),
                                ((Number) row[3]).intValue()),
                        (BigDecimal) row[4]))
                .toList();
    }

    /** 지정한 기수에 속한 셀렉터스의 기수 이력만 반환한다. */
    public List<GenerationMembership> findGenerationMemberships(
            List<Long> selectorIds, List<Long> generationIds) {
        if (selectorIds.isEmpty() || generationIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                        select sg.selectorsId, g.id, g.generationName
                        from SelectorsGeneration sg
                        join Generation g on g.id = sg.generationId
                        where sg.selectorsId in :selectorIds
                          and sg.generationId in :generationIds
                        order by sg.selectorsId, g.activityStartDate desc, g.id desc
                        """, Object[].class)
                .setParameter("selectorIds", selectorIds)
                .setParameter("generationIds", generationIds)
                .getResultList().stream()
                .map(row -> new GenerationMembership(
                        (Long) row[0], (Long) row[1], (String) row[2]))
                .toList();
    }

    public List<DatedSales> summarizeConfirmedSalesByMonth(
            List<Long> selectorIds,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive) {
        return summarizeConfirmedSalesByDatePart(selectorIds, startInclusive, endExclusive, false);
    }

    public List<SelectorCount> countProductClicks(
            List<Long> selectorIds,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive) {
        if (selectorIds.isEmpty()) {
            return List.of();
        }
        StringBuilder jpql = new StringBuilder("""
                select c.selectorsId, count(c)
                from ClickLog c
                where c.selectorsId in :selectorIds
                  and c.linkType = :linkType
                """);
        appendCreatedAtPeriod(jpql, startInclusive, endExclusive);
        jpql.append(" group by c.selectorsId");
        TypedQuery<Object[]> query = entityManager.createQuery(jpql.toString(), Object[].class)
                .setParameter("selectorIds", selectorIds)
                .setParameter("linkType", ViewPageType.PRODUCT);
        bindCreatedAtPeriod(query, startInclusive, endExclusive);
        return query.getResultList().stream()
                .map(row -> new SelectorCount((Long) row[0], ((Number) row[1]).longValue()))
                .toList();
    }

    public List<SelectorCount> countContents(
            List<Long> selectorIds,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive) {
        if (selectorIds.isEmpty()) {
            return List.of();
        }
        StringBuilder jpql = new StringBuilder("""
                select content.selectorsId, count(content)
                from Content content
                where content.selectorsId in :selectorIds
                  and content.deleted = false
                """);
        appendCreatedAtPeriod(jpql, "content.createdAt", startInclusive, endExclusive);
        jpql.append(" group by content.selectorsId");
        TypedQuery<Object[]> query = entityManager.createQuery(jpql.toString(), Object[].class)
                .setParameter("selectorIds", selectorIds);
        bindCreatedAtPeriod(query, startInclusive, endExclusive);
        return query.getResultList().stream()
                .map(row -> new SelectorCount((Long) row[0], ((Number) row[1]).longValue()))
                .toList();
    }

    public List<SelectorSnsProfile> findSnsProfiles(List<Long> selectorIds) {
        if (selectorIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                        select account.selectorsId, account.profileImageUrl,
                               account.snsCode, account.followerCount
                        from SelectorsSnsAccount account
                        where account.selectorsId in :selectorIds
                          and account.deleted = false
                        """, Object[].class)
                .setParameter("selectorIds", selectorIds)
                .getResultList().stream()
                .map(row -> new SelectorSnsProfile(
                        (Long) row[0],
                        (String) row[1],
                        (SnsPlatform) row[2],
                        (Long) row[3]))
                .toList();
    }

    /** 한 셀렉터스의 확정 매출을 상품별로 집계한다(매출 내림차순). */
    public List<ProductSales> summarizeConfirmedSalesByProduct(
            Long selectorId, LocalDateTime startInclusive, LocalDateTime endExclusive) {
        StringBuilder jpql = new StringBuilder("""
                select p.productId, prod.productName, prod.brandName, prod.thumbnailUrl,
                       prod.category, coalesce(sum(p.paidAmount), 0),
                       count(distinct p.orderNo), coalesce(sum(p.quantity), 0)
                from PurchaseHistory p
                join Selectors s on s.id = p.selectorsId
                join Product prod on prod.id = p.productId
                where p.selectorsId = :selectorId
                  and s.deleted = false
                  and p.status = :status
                  and p.confirmedAt is not null
                  and (s.userId is null or p.userId <> s.userId)
                """);
        appendConfirmedAtPeriod(jpql, startInclusive, endExclusive);
        jpql.append(" group by p.productId, prod.productName, prod.brandName,"
                + " prod.thumbnailUrl, prod.category order by sum(p.paidAmount) desc");
        return singleSelectorQuery(jpql.toString(), selectorId, startInclusive, endExclusive)
                .getResultList().stream()
                .map(row -> new ProductSales(
                        (Long) row[0], (String) row[1], (String) row[2], (String) row[3],
                        (String) row[4], (BigDecimal) row[5],
                        ((Number) row[6]).longValue(), ((Number) row[7]).longValue()))
                .toList();
    }

    /**
     * 한 셀렉터스의 확정 매출을 캠페인별로 집계한다(매출 내림차순).
     *
     * <p>ponytail: 한 상품이 여러 캠페인에 속하면 매출이 각 캠페인에 중복 계상된다.
     * 개인 두각 확인용 차트라 허용하며, 정확 귀속이 필요하면 캠페인 기간 교집합으로 좁힌다.
     */
    public List<CampaignSales> summarizeConfirmedSalesByCampaign(
            Long selectorId, LocalDateTime startInclusive, LocalDateTime endExclusive) {
        StringBuilder jpql = new StringBuilder("""
                select c.id, c.title, coalesce(sum(p.paidAmount), 0),
                       count(distinct p.orderNo), coalesce(sum(p.quantity), 0)
                from PurchaseHistory p
                join Selectors s on s.id = p.selectorsId
                join CampaignProduct cp on cp.product.id = p.productId
                join Campaign c on c.id = cp.campaign.id
                where p.selectorsId = :selectorId
                  and s.deleted = false
                  and c.isDeleted = false
                  and p.status = :status
                  and p.confirmedAt is not null
                  and (s.userId is null or p.userId <> s.userId)
                """);
        appendConfirmedAtPeriod(jpql, startInclusive, endExclusive);
        jpql.append(" group by c.id, c.title order by sum(p.paidAmount) desc");
        return singleSelectorQuery(jpql.toString(), selectorId, startInclusive, endExclusive)
                .getResultList().stream()
                .map(row -> new CampaignSales(
                        (Long) row[0], (String) row[1], (BigDecimal) row[2],
                        ((Number) row[3]).longValue(), ((Number) row[4]).longValue()))
                .toList();
    }

    private TypedQuery<Object[]> singleSelectorQuery(
            String jpql, Long selectorId,
            LocalDateTime startInclusive, LocalDateTime endExclusive) {
        TypedQuery<Object[]> query = entityManager.createQuery(jpql, Object[].class)
                .setParameter("selectorId", selectorId)
                .setParameter("status", PurchaseStatus.PURCHASE_CONFIRMED);
        bindConfirmedAtPeriod(query, startInclusive, endExclusive);
        return query;
    }

    private List<DatedSales> summarizeConfirmedSalesByDatePart(
            List<Long> selectorIds,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive,
            boolean daily) {
        if (selectorIds.isEmpty()) {
            return List.of();
        }
        String dateSelect = daily
                ? "year(p.confirmedAt), month(p.confirmedAt), day(p.confirmedAt)"
                : "year(p.confirmedAt), month(p.confirmedAt)";
        StringBuilder jpql = new StringBuilder("""
                select %s, coalesce(sum(p.paidAmount), 0), count(distinct p.orderNo)
                from PurchaseHistory p
                join Selectors s on s.id = p.selectorsId
                where p.selectorsId in :selectorIds
                  and s.deleted = false
                  and p.status = :status
                  and p.confirmedAt is not null
                  and (s.userId is null or p.userId <> s.userId)
                """.formatted(dateSelect));
        appendConfirmedAtPeriod(jpql, startInclusive, endExclusive);
        jpql.append(daily
                ? " group by year(p.confirmedAt), month(p.confirmedAt), day(p.confirmedAt)"
                : " group by year(p.confirmedAt), month(p.confirmedAt)");
        return confirmedSalesQuery(jpql.toString(), selectorIds, startInclusive, endExclusive)
                .getResultList().stream()
                .map(row -> toDatedSales(row, daily))
                .toList();
    }

    private DatedSales toDatedSales(Object[] row, boolean daily) {
        int year = ((Number) row[0]).intValue();
        int month = ((Number) row[1]).intValue();
        int day = daily ? ((Number) row[2]).intValue() : 1;
        int salesIndex = daily ? 3 : 2;
        return new DatedSales(
                LocalDate.of(year, month, day),
                (BigDecimal) row[salesIndex],
                ((Number) row[salesIndex + 1]).longValue());
    }

    private TypedQuery<Object[]> confirmedSalesQuery(
            String jpql,
            List<Long> selectorIds,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive) {
        TypedQuery<Object[]> query = entityManager.createQuery(jpql, Object[].class)
                .setParameter("selectorIds", selectorIds)
                .setParameter("status", PurchaseStatus.PURCHASE_CONFIRMED);
        bindConfirmedAtPeriod(query, startInclusive, endExclusive);
        return query;
    }

    private void appendConfirmedAtPeriod(
            StringBuilder jpql, LocalDateTime startInclusive, LocalDateTime endExclusive) {
        if (startInclusive != null) {
            jpql.append(" and p.confirmedAt >= :startInclusive");
        }
        if (endExclusive != null) {
            jpql.append(" and p.confirmedAt < :endExclusive");
        }
    }

    private void appendCreatedAtPeriod(
            StringBuilder jpql, LocalDateTime startInclusive, LocalDateTime endExclusive) {
        appendCreatedAtPeriod(jpql, "c.createdAt", startInclusive, endExclusive);
    }

    private void appendCreatedAtPeriod(
            StringBuilder jpql,
            String column,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive) {
        if (startInclusive != null) {
            jpql.append(" and ").append(column).append(" >= :startInclusive");
        }
        if (endExclusive != null) {
            jpql.append(" and ").append(column).append(" < :endExclusive");
        }
    }

    private void bindConfirmedAtPeriod(
            TypedQuery<Object[]> query,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive) {
        if (startInclusive != null) {
            query.setParameter("startInclusive", startInclusive);
        }
        if (endExclusive != null) {
            query.setParameter("endExclusive", endExclusive);
        }
    }

    private void bindCreatedAtPeriod(
            TypedQuery<Object[]> query,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive) {
        bindConfirmedAtPeriod(query, startInclusive, endExclusive);
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

    public record ConfirmedSalesComparison(
            Long selectorId,
            BigDecimal currentTotalSales,
            long currentConfirmedOrderCount,
            BigDecimal previousTotalSales,
            long previousConfirmedOrderCount
    ) {
    }

    public record DatedSales(
            LocalDate date,
            BigDecimal totalSales,
            long confirmedOrderCount
    ) {
    }

    public record DatedSelectorSales(
            Long selectorId,
            LocalDate date,
            BigDecimal totalSales
    ) {
    }

    public record SelectorCount(
            Long selectorId,
            long count
    ) {
    }

    public record SelectorSnsProfile(
            Long selectorId,
            String profileImageUrl,
            SnsPlatform snsCode,
            Long followerCount
    ) {
    }

    public record ProductSales(
            Long productId,
            String productName,
            String brandName,
            String thumbnailUrl,
            String category,
            BigDecimal totalSales,
            long confirmedOrderCount,
            long soldQuantity
    ) {
    }

    public record CampaignSales(
            Long campaignId,
            String title,
            BigDecimal totalSales,
            long confirmedOrderCount,
            long soldQuantity
    ) {
    }
}
