package com.fuma.hiselectors.purchase.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.config.CacheConfig;
import com.fuma.hiselectors.purchase.model.PurchaseHistory;
import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.selectors.model.Selectors;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(CacheConfig.class)
class PurchaseHistoryRepositoryTest {

    @Autowired
    private PurchaseHistoryRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void countsPurchaseMadeBeforeActivityEndWhenConfirmedAfterSelectorDeactivation() {
        LocalDateTime activityMonthStart = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime activityMonthEnd = LocalDateTime.of(2026, 9, 1, 0, 0);
        Selectors selectors = entityManager.persist(Selectors.builder()
                .userId(101L)
                .selectorsRoleId(Selectors.ACTIVE_ROLE)
                .selectorsCode("SEL-0101")
                .selectorsNickname("정산셀렉터")
                .build());
        PurchaseHistory purchase = entityManager.persist(PurchaseHistory.builder()
                .orderNo("ORDER-BEFORE-ACTIVITY-END")
                .userId(201L)
                .selectorsId(selectors.getId())
                .productId(301L)
                .quantity(1)
                .regularUnitPrice(new BigDecimal("19000"))
                .saleUnitPrice(new BigDecimal("19000"))
                .discountAmount(BigDecimal.ZERO)
                .paidAmount(new BigDecimal("19000"))
                .purchasedAt(activityMonthEnd.minusSeconds(1))
                .build());
        entityManager.flush();

        selectors.deactivate();
        purchase.transitionTo(
                PurchaseStatus.PURCHASE_CONFIRMED, activityMonthEnd.plusDays(7));
        entityManager.flush();
        entityManager.clear();

        PurchaseSettlementSummary summary = repository
                .summarizeConfirmedPurchasesForActivityMonth(
                        selectors.getId(),
                        PurchaseStatus.PURCHASE_CONFIRMED,
                        activityMonthStart,
                        activityMonthEnd);

        assertThat(summary.getConfirmedPurchaseCount()).isEqualTo(1L);
        assertThat(summary.getTotalSales()).isEqualByComparingTo("19000");
    }
}
