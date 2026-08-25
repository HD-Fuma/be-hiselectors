package com.fuma.hiselectors.performance.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.model.GenerationStatus;
import com.fuma.hiselectors.performance.repository.SelectorPerformanceQueryRepository.ConfirmedSales;
import com.fuma.hiselectors.purchase.model.PurchaseHistory;
import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.model.SelectorsGeneration;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({SelectorPerformanceQueryRepository.class,
        SelectorPerformanceQueryRepositoryTest.CacheConfig.class})
class SelectorPerformanceQueryRepositoryTest {

    @Autowired
    private SelectorPerformanceQueryRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void aggregatesConfirmedSalesByConfirmationDateAndExcludesSelfPurchases() {
        Selectors selector = entityManager.persist(selector(101L, "SEL-1", "정상"));
        Selectors other = entityManager.persist(selector(102L, "SEL-2", "다른"));
        Selectors deleted = entityManager.persist(selector(103L, "SEL-3", "삭제"));
        deleted.softDelete();

        Generation oldGeneration = entityManager.persist(generation(
                "3기", LocalDateTime.of(2026, 1, 1, 0, 0)));
        Generation latestGeneration = entityManager.persist(generation(
                "4기", LocalDateTime.of(2026, 7, 1, 0, 0)));
        entityManager.persist(SelectorsGeneration.builder()
                .selectorsId(selector.getId()).generationId(oldGeneration.getId()).build());
        entityManager.persist(SelectorsGeneration.builder()
                .selectorsId(selector.getId()).generationId(latestGeneration.getId()).build());

        entityManager.persist(confirmedPurchase(
                selector.getId(), 201L, 1L, "ORD-1", "100", "2026-08-10T10:00:00"));
        entityManager.persist(confirmedPurchase(
                selector.getId(), 202L, 2L, "ORD-1", "150", "2026-08-10T11:00:00"));
        entityManager.persist(confirmedPurchase(
                selector.getId(), 101L, 3L, "ORD-SELF", "999", "2026-08-11T10:00:00"));
        entityManager.persist(confirmedPurchase(
                selector.getId(), 203L, 4L, "ORD-OLD", "500", "2026-07-31T23:59:59"));
        entityManager.persist(unconfirmedPurchase(
                selector.getId(), 204L, 5L, "ORD-WAIT", "700", "2026-08-12T10:00:00"));
        entityManager.persist(confirmedPurchase(
                other.getId(), 205L, 6L, "ORD-2", "300", "2026-08-15T10:00:00"));
        entityManager.persist(confirmedPurchase(
                deleted.getId(), 206L, 7L, "ORD-3", "400", "2026-08-15T10:00:00"));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findAllVisibleSelectors())
                .extracting(Selectors::getSelectorsNickname)
                .containsExactly("정상", "다른");
        assertThat(repository.findGenerationMemberships(
                List.of(selector.getId(), other.getId())))
                .extracting(row -> row.generationName())
                .containsExactly("4기", "3기");

        List<ConfirmedSales> result = repository.summarizeConfirmedSales(
                List.of(selector.getId(), other.getId(), deleted.getId()),
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 9, 1, 0, 0));

        assertThat(result).hasSize(2);
        assertThat(result).filteredOn(row -> row.selectorId().equals(selector.getId()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.totalSales()).isEqualByComparingTo("250");
                    assertThat(row.confirmedOrderCount()).isEqualTo(1L);
                });
        assertThat(result).filteredOn(row -> row.selectorId().equals(other.getId()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.totalSales()).isEqualByComparingTo("300");
                    assertThat(row.confirmedOrderCount()).isEqualTo(1L);
                });
    }

    private Selectors selector(Long userId, String code, String nickname) {
        return Selectors.builder()
                .userId(userId)
                .selectorsRoleId("ACTIVE")
                .selectorsCode(code)
                .selectorsNickname(nickname)
                .build();
    }

    private Generation generation(String name, LocalDateTime activityStartDate) {
        return Generation.builder()
                .generationName(name)
                .startDate(activityStartDate.minusMonths(1))
                .endDate(activityStartDate.minusDays(1))
                .activityStartDate(activityStartDate)
                .activityEndDate(activityStartDate.plusMonths(3))
                .status(GenerationStatus.INACTIVE)
                .build();
    }

    private PurchaseHistory confirmedPurchase(
            Long selectorId, Long userId, Long productId, String orderNo,
            String amount, String confirmedAt) {
        PurchaseHistory purchase = purchase(
                selectorId, userId, productId, orderNo, amount);
        purchase.transitionTo(
                PurchaseStatus.PURCHASE_CONFIRMED, LocalDateTime.parse(confirmedAt));
        return purchase;
    }

    private PurchaseHistory unconfirmedPurchase(
            Long selectorId, Long userId, Long productId, String orderNo,
            String amount, String purchasedAt) {
        return purchase(selectorId, userId, productId, orderNo, amount,
                LocalDateTime.parse(purchasedAt));
    }

    private PurchaseHistory purchase(
            Long selectorId, Long userId, Long productId, String orderNo, String amount) {
        return purchase(selectorId, userId, productId, orderNo, amount,
                LocalDateTime.of(2026, 8, 1, 0, 0));
    }

    private PurchaseHistory purchase(
            Long selectorId, Long userId, Long productId, String orderNo,
            String amount, LocalDateTime purchasedAt) {
        BigDecimal paidAmount = new BigDecimal(amount);
        return PurchaseHistory.builder()
                .orderNo(orderNo)
                .userId(userId)
                .selectorsId(selectorId)
                .productId(productId)
                .quantity(1)
                .regularUnitPrice(paidAmount)
                .saleUnitPrice(paidAmount)
                .discountAmount(BigDecimal.ZERO)
                .paidAmount(paidAmount)
                .purchasedAt(purchasedAt)
                .build();
    }

    @TestConfiguration
    static class CacheConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }
}
