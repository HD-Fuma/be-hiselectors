package com.fuma.hiselectors.performance.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.analytics.model.ClickLog;
import com.fuma.hiselectors.analytics.model.ViewPageType;
import com.fuma.hiselectors.product.model.Product;
import com.fuma.hiselectors.product.model.ProductStatus;
import com.fuma.hiselectors.purchase.model.PurchaseHistory;
import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({PerformanceQueryRepository.class, PerformanceQueryRepositoryTest.CacheConfig.class})
class PerformanceQueryRepositoryTest {

    @Autowired
    private PerformanceQueryRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void aggregatesOnlyProductClicksAndPerformancePurchaseStatusesForSelectorAndMonth() {
        LocalDateTime purchasedAt = LocalDateTime.now().withNano(0);
        YearMonth month = YearMonth.from(purchasedAt);
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();
        Product product = entityManager.persist(product("P-1"));

        entityManager.persist(clickLog(
                9L, ViewPageType.PRODUCT, product.getId(), purchasedAt));
        entityManager.persist(clickLog(
                9L, ViewPageType.PRODUCT, product.getId(), purchasedAt));
        entityManager.persist(clickLog(9L, ViewPageType.SHOP, 9L, purchasedAt));
        entityManager.persist(clickLog(
                10L, ViewPageType.PRODUCT, product.getId(), purchasedAt));
        entityManager.persist(confirmedPurchase(9L, product.getId(), "ORD-1", purchasedAt, "10000"));
        entityManager.persist(unconfirmedPurchase(9L, product.getId(), "ORD-2", purchasedAt, "20000"));
        entityManager.persist(confirmedPurchase(10L, product.getId(), "ORD-3", purchasedAt, "30000"));
        PurchaseHistory canceled = unconfirmedPurchase(
                9L, product.getId(), "ORD-4", purchasedAt, "40000");
        canceled.transitionTo(PurchaseStatus.CANCEL_REQUESTED, null);
        canceled.transitionTo(PurchaseStatus.CANCELED, null);
        entityManager.persist(canceled);
        PurchaseHistory returned = confirmedPurchase(
                9L, product.getId(), "ORD-5", purchasedAt, "50000");
        returned.transitionTo(PurchaseStatus.RETURN_REQUESTED, null);
        returned.transitionTo(PurchaseStatus.RETURNED, null);
        entityManager.persist(returned);
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.countProductClicks(9L, start, end)).isEqualTo(2L);
        assertThat(repository.summarizePerformancePurchases(9L, start, end))
                .satisfies(summary -> {
                    assertThat(summary.conversionCount()).isEqualTo(2L);
                    assertThat(summary.conversionAmount()).isEqualByComparingTo("30000");
                });
        assertThat(repository.findDailyProductClicks(9L, start, end))
                .singleElement()
                .satisfies(day -> assertThat(day.clickCount()).isEqualTo(2L));
        assertThat(repository.findDailyPerformancePurchases(9L, start, end))
                .singleElement()
                .satisfies(day -> {
                    assertThat(day.conversionCount()).isEqualTo(2L);
                    assertThat(day.conversionAmount()).isEqualByComparingTo("30000");
                });
        assertThat(repository.findProductClicks(9L, start, end))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.productId()).isEqualTo(product.getId());
                    assertThat(row.clickCount()).isEqualTo(2L);
                });
        assertThat(repository.findProductPerformancePurchases(9L, start, end))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.productId()).isEqualTo(product.getId());
                    assertThat(row.conversionCount()).isEqualTo(2L);
                    assertThat(row.conversionAmount()).isEqualByComparingTo("30000");
                });
    }

    private Product product(String productCode) {
        return Product.builder()
                .productCode(productCode)
                .productName("상품")
                .brandName("브랜드")
                .category("카테고리")
                .regularPrice(new BigDecimal("12000"))
                .salePrice(new BigDecimal("10000"))
                .status(ProductStatus.ON_SALE)
                .thumbnailUrl("https://example.com/product.jpg")
                .detailUrl("https://example.com/product")
                .build();
    }

    private ClickLog clickLog(
            Long selectorsId, ViewPageType type, Long referenceId, LocalDateTime createdAt) {
        ClickLog clickLog = new ClickLog(selectorsId, type, referenceId, null);
        ReflectionTestUtils.setField(clickLog, "createdAt", createdAt);
        return clickLog;
    }

    private PurchaseHistory confirmedPurchase(
            Long selectorsId, Long productId, String orderNo,
            LocalDateTime purchasedAt, String amount) {
        PurchaseHistory purchase = purchase(
                selectorsId, productId, orderNo, purchasedAt, amount);
        purchase.transitionTo(PurchaseStatus.PURCHASE_CONFIRMED, purchasedAt.plusDays(7));
        return purchase;
    }

    private PurchaseHistory unconfirmedPurchase(
            Long selectorsId, Long productId, String orderNo,
            LocalDateTime purchasedAt, String amount) {
        return purchase(selectorsId, productId, orderNo, purchasedAt, amount);
    }

    private PurchaseHistory purchase(
            Long selectorsId, Long productId, String orderNo,
            LocalDateTime purchasedAt, String amount) {
        BigDecimal paidAmount = new BigDecimal(amount);
        return PurchaseHistory.builder()
                .orderNo(orderNo)
                .userId(100L)
                .selectorsId(selectorsId)
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
