package com.fuma.hiselectors.purchase.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.config.CacheConfig;
import com.fuma.hiselectors.product.model.Product;
import com.fuma.hiselectors.product.model.ProductStatus;
import com.fuma.hiselectors.purchase.model.PurchaseHistory;
import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.settlement.dto.SettlementPurchaseHistoryResponse;
import com.fuma.hiselectors.user.model.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

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

    @Test
    void cursorQueryUsesIdAsTieBreakerForPurchasesAtTheSameTime() {
        User user = entityManager.persist(User.builder()
                .hiId("cursor-buyer")
                .name("cursor buyer")
                .build());
        Selectors selectors = entityManager.persist(Selectors.builder()
                .userId(user.getId())
                .selectorsRoleId(Selectors.ACTIVE_ROLE)
                .selectorsCode("SEL-CURSOR")
                .selectorsNickname("cursor")
                .build());
        Product product = entityManager.persist(Product.builder()
                .productCode("PRODUCT-CURSOR")
                .regularPrice(new BigDecimal("10000"))
                .salePrice(new BigDecimal("10000"))
                .status(ProductStatus.ON_SALE)
                .build());
        LocalDateTime samePurchasedAt = LocalDateTime.of(2026, 8, 31, 12, 0);
        PurchaseHistory first = entityManager.persist(
                purchase("ORDER-CURSOR-1", user.getId(), selectors.getId(), product.getId(),
                        samePurchasedAt));
        PurchaseHistory second = entityManager.persist(
                purchase("ORDER-CURSOR-2", user.getId(), selectors.getId(), product.getId(),
                        samePurchasedAt));
        PurchaseHistory third = entityManager.persist(
                purchase("ORDER-CURSOR-3", user.getId(), selectors.getId(), product.getId(),
                        samePurchasedAt));
        PurchaseHistory older = entityManager.persist(
                purchase("ORDER-CURSOR-4", user.getId(), selectors.getId(), product.getId(),
                        samePurchasedAt.minusSeconds(1)));
        entityManager.flush();
        entityManager.clear();

        List<SettlementPurchaseHistoryResponse> firstPage = repository
                .searchCursorForSettlementAdminBySelectorsId(
                        selectors.getId(), null, null, null, null, PageRequest.of(0, 3));
        List<SettlementPurchaseHistoryResponse> nextPage = repository
                .searchCursorForSettlementAdminBySelectorsId(
                        selectors.getId(), null, null, samePurchasedAt, second.getId(),
                        PageRequest.of(0, 3));

        assertThat(firstPage).extracting(SettlementPurchaseHistoryResponse::purchaseHistoryId)
                .containsExactly(third.getId(), second.getId(), first.getId());
        assertThat(nextPage).extracting(SettlementPurchaseHistoryResponse::purchaseHistoryId)
                .containsExactly(first.getId(), older.getId());
    }

    private PurchaseHistory purchase(
            String orderNo,
            Long userId,
            Long selectorsId,
            Long productId,
            LocalDateTime purchasedAt) {
        return PurchaseHistory.builder()
                .orderNo(orderNo)
                .userId(userId)
                .selectorsId(selectorsId)
                .productId(productId)
                .quantity(1)
                .regularUnitPrice(new BigDecimal("10000"))
                .saleUnitPrice(new BigDecimal("10000"))
                .discountAmount(BigDecimal.ZERO)
                .paidAmount(new BigDecimal("10000"))
                .purchasedAt(purchasedAt)
                .build();
    }
}
