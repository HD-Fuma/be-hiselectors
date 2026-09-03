package com.fuma.hiselectors.performance.repository;

import com.fuma.hiselectors.analytics.model.ViewPageType;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.selectors.model.Selectors;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SelectorPerformanceQueryRepository {

    static final String PURCHASE_CONFIRMED_INDEX = "idx_purchase_selector_status_confirmed";
    static final String CLICK_CREATED_INDEX = "idx_click_selector_type_created";
    static final String CONTENT_CREATED_INDEX = "idx_content_selector_deleted_created";

    private final EntityManager entityManager;
    private Boolean mysql;

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
     * MySQL에서는 정산용 purchased_at 인덱스가 아니라 confirmed_at 인덱스를 강제한다.
     */
    public List<ConfirmedSales> summarizeConfirmedSales(
            List<Long> selectorIds,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive) {
        if (selectorIds.isEmpty()) {
            return List.of();
        }

        StringBuilder sql = new StringBuilder("""
                select p.selectors_id, coalesce(sum(p.paid_amount), 0), count(distinct p.order_no)
                from %s
                join selectors s on s.selectors_id = p.selectors_id
                where p.selectors_id in (:selectorIds)
                  and s.is_deleted = false
                  and p.status = :status
                  and p.confirmed_at is not null
                  and (s.user_id is null or p.user_id <> s.user_id)
                """.formatted(indexedTable("purchase_history", "p", PURCHASE_CONFIRMED_INDEX)));
        appendNativePeriod(sql, "p.confirmed_at", startInclusive, endExclusive);
        sql.append(" group by p.selectors_id");

        return nativeConfirmedSalesQuery(sql.toString(), selectorIds, startInclusive, endExclusive)
                .stream()
                .map(row -> new ConfirmedSales(
                        ((Number) row[0]).longValue(),
                        asBigDecimal(row[1]),
                        ((Number) row[2]).longValue()))
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
        StringBuilder sql = new StringBuilder("""
                select p.selectors_id,
                       year(p.confirmed_at), month(p.confirmed_at), day(p.confirmed_at),
                       coalesce(sum(p.paid_amount), 0)
                from %s
                join selectors s on s.selectors_id = p.selectors_id
                where p.selectors_id in (:selectorIds)
                  and s.is_deleted = false
                  and p.status = :status
                  and p.confirmed_at is not null
                  and (s.user_id is null or p.user_id <> s.user_id)
                """.formatted(indexedTable("purchase_history", "p", PURCHASE_CONFIRMED_INDEX)));
        appendNativePeriod(sql, "p.confirmed_at", startInclusive, endExclusive);
        sql.append(" group by p.selectors_id, year(p.confirmed_at),"
                + " month(p.confirmed_at), day(p.confirmed_at)");
        return nativeConfirmedSalesQuery(sql.toString(), selectorIds, startInclusive, endExclusive)
                .stream()
                .map(row -> new DatedSelectorSales(
                        ((Number) row[0]).longValue(),
                        LocalDate.of(
                                ((Number) row[1]).intValue(),
                                ((Number) row[2]).intValue(),
                                ((Number) row[3]).intValue()),
                        asBigDecimal(row[4])))
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
        StringBuilder sql = new StringBuilder("""
                select c.selectors_id, count(*)
                from %s
                where c.selectors_id in (:selectorIds)
                  and c.link_type = :linkType
                """.formatted(indexedTable("click_log", "c", CLICK_CREATED_INDEX)));
        appendNativePeriod(sql, "c.created_at", startInclusive, endExclusive);
        sql.append(" group by c.selectors_id");
        Query query = entityManager.createNativeQuery(sql.toString())
                .setParameter("selectorIds", selectorIds)
                .setParameter("linkType", ViewPageType.PRODUCT.name());
        bindNativePeriod(query, startInclusive, endExclusive);
        return nativeRows(query).stream()
                .map(row -> new SelectorCount(
                        ((Number) row[0]).longValue(), ((Number) row[1]).longValue()))
                .toList();
    }

    public List<SelectorCount> countContents(
            List<Long> selectorIds,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive) {
        if (selectorIds.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder("""
                select content.selectors_id, count(*)
                from %s
                where content.selectors_id in (:selectorIds)
                  and content.is_deleted = false
                """.formatted(indexedTable("content", "content", CONTENT_CREATED_INDEX)));
        appendNativePeriod(sql, "content.created_at", startInclusive, endExclusive);
        sql.append(" group by content.selectors_id");
        Query query = entityManager.createNativeQuery(sql.toString())
                .setParameter("selectorIds", selectorIds);
        bindNativePeriod(query, startInclusive, endExclusive);
        return nativeRows(query).stream()
                .map(row -> new SelectorCount(
                        ((Number) row[0]).longValue(), ((Number) row[1]).longValue()))
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
        StringBuilder sql = new StringBuilder("""
                select p.product_id, prod.product_name, prod.brand_name, prod.thumbnail_url,
                       prod.category, coalesce(sum(p.paid_amount), 0),
                       count(distinct p.order_no), coalesce(sum(p.quantity), 0)
                from %s
                join selectors s on s.selectors_id = p.selectors_id
                join product prod on prod.product_id = p.product_id
                where p.selectors_id = :selectorId
                  and s.is_deleted = false
                  and p.status = :status
                  and p.confirmed_at is not null
                  and (s.user_id is null or p.user_id <> s.user_id)
                """.formatted(indexedTable("purchase_history", "p", PURCHASE_CONFIRMED_INDEX)));
        appendNativePeriod(sql, "p.confirmed_at", startInclusive, endExclusive);
        sql.append(" group by p.product_id, prod.product_name, prod.brand_name,"
                + " prod.thumbnail_url, prod.category order by sum(p.paid_amount) desc");
        return nativeSingleSelectorQuery(sql.toString(), selectorId, startInclusive, endExclusive)
                .stream()
                .map(row -> new ProductSales(
                        ((Number) row[0]).longValue(), (String) row[1], (String) row[2],
                        (String) row[3], (String) row[4], asBigDecimal(row[5]),
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
        StringBuilder sql = new StringBuilder("""
                select c.campaign_id, c.title, coalesce(sum(p.paid_amount), 0),
                       count(distinct p.order_no), coalesce(sum(p.quantity), 0)
                from %s
                join selectors s on s.selectors_id = p.selectors_id
                join campaign_product cp on cp.product_id = p.product_id
                join campaign c on c.campaign_id = cp.campaign_id
                where p.selectors_id = :selectorId
                  and s.is_deleted = false
                  and c.is_deleted = false
                  and p.status = :status
                  and p.confirmed_at is not null
                  and (s.user_id is null or p.user_id <> s.user_id)
                """.formatted(indexedTable("purchase_history", "p", PURCHASE_CONFIRMED_INDEX)));
        appendNativePeriod(sql, "p.confirmed_at", startInclusive, endExclusive);
        sql.append(" group by c.campaign_id, c.title order by sum(p.paid_amount) desc");
        return nativeSingleSelectorQuery(sql.toString(), selectorId, startInclusive, endExclusive)
                .stream()
                .map(row -> new CampaignSales(
                        ((Number) row[0]).longValue(), (String) row[1], asBigDecimal(row[2]),
                        ((Number) row[3]).longValue(), ((Number) row[4]).longValue()))
                .toList();
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
                ? "year(p.confirmed_at), month(p.confirmed_at), day(p.confirmed_at)"
                : "year(p.confirmed_at), month(p.confirmed_at)";
        StringBuilder sql = new StringBuilder("""
                select %s, coalesce(sum(p.paid_amount), 0), count(distinct p.order_no)
                from %s
                join selectors s on s.selectors_id = p.selectors_id
                where p.selectors_id in (:selectorIds)
                  and s.is_deleted = false
                  and p.status = :status
                  and p.confirmed_at is not null
                  and (s.user_id is null or p.user_id <> s.user_id)
                """.formatted(
                dateSelect,
                indexedTable("purchase_history", "p", PURCHASE_CONFIRMED_INDEX)));
        appendNativePeriod(sql, "p.confirmed_at", startInclusive, endExclusive);
        sql.append(daily
                ? " group by year(p.confirmed_at), month(p.confirmed_at), day(p.confirmed_at)"
                : " group by year(p.confirmed_at), month(p.confirmed_at)");
        return nativeConfirmedSalesQuery(sql.toString(), selectorIds, startInclusive, endExclusive)
                .stream()
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
                asBigDecimal(row[salesIndex]),
                ((Number) row[salesIndex + 1]).longValue());
    }

    private List<Object[]> nativeConfirmedSalesQuery(
            String sql,
            List<Long> selectorIds,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive) {
        Query query = entityManager.createNativeQuery(sql)
                .setParameter("selectorIds", selectorIds)
                .setParameter("status", PurchaseStatus.PURCHASE_CONFIRMED.name());
        bindNativePeriod(query, startInclusive, endExclusive);
        return nativeRows(query);
    }

    private List<Object[]> nativeSingleSelectorQuery(
            String sql, Long selectorId,
            LocalDateTime startInclusive, LocalDateTime endExclusive) {
        Query query = entityManager.createNativeQuery(sql)
                .setParameter("selectorId", selectorId)
                .setParameter("status", PurchaseStatus.PURCHASE_CONFIRMED.name());
        bindNativePeriod(query, startInclusive, endExclusive);
        return nativeRows(query);
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> nativeRows(Query query) {
        return query.getResultList();
    }

    private void appendNativePeriod(
            StringBuilder sql,
            String column,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive) {
        if (startInclusive != null) {
            sql.append(" and ").append(column).append(" >= :startInclusive");
        }
        if (endExclusive != null) {
            sql.append(" and ").append(column).append(" < :endExclusive");
        }
    }

    private void bindNativePeriod(
            Query query,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive) {
        if (startInclusive != null) {
            query.setParameter("startInclusive", startInclusive);
        }
        if (endExclusive != null) {
            query.setParameter("endExclusive", endExclusive);
        }
    }

    private String indexedTable(String table, String alias, String indexName) {
        if (!mysql()) {
            return table + " " + alias;
        }
        return table + " " + alias + " FORCE INDEX (" + indexName + ")";
    }

    private boolean mysql() {
        if (mysql == null) {
            mysql = entityManager.getEntityManagerFactory()
                    .unwrap(SessionFactoryImplementor.class)
                    .getJdbcServices()
                    .getDialect() instanceof MySQLDialect;
        }
        return mysql;
    }

    private static BigDecimal asBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        throw new IllegalStateException("numeric column was " + value);
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
