package com.fuma.hiselectors.performance.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.product.model.Product;
import com.fuma.hiselectors.product.model.ProductStatus;
import com.fuma.hiselectors.productgroup.model.ProductGroup;
import com.fuma.hiselectors.productgroup.model.ProductGroupItem;
import com.fuma.hiselectors.purchase.model.PurchaseHistory;
import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
@Import({CampaignPerformanceQueryRepository.class,
        CampaignPerformanceQueryRepositoryTest.CacheConfig.class})
class CampaignPerformanceQueryRepositoryTest {

    @Autowired
    private CampaignPerformanceQueryRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void attributesEachTerminalPurchaseOnceUsingCurrentPrePurchaseGroupMembership() {
        LocalDateTime purchasedAt = LocalDateTime.of(2026, 8, 10, 12, 0);
        Selectors selector = entityManager.persist(selector("SEL-1", "첫째"));
        Selectors accountlessSelector = entityManager.persist(
                selector("SEL-NO-PROFILE", "프로필 없음"));
        Selectors lateSelector = entityManager.persist(selector("SEL-2", "늦음"));
        Selectors selfPurchasingSelector = entityManager.persist(
                selector("SEL-3", "본인 구매", 999L));
        Selectors deletedSelector = selector("SEL-4", "삭제됨");
        deletedSelector.softDelete();
        entityManager.persist(deletedSelector);
        Product product = entityManager.persist(product("P-1"));
        Product otherProduct = entityManager.persist(product("P-2"));
        entityManager.persist(SelectorsSnsAccount.builder()
                .selectorsId(selector.getId())
                .snsCode(SnsPlatform.INSTAGRAM)
                .accountId("first-selector")
                .profileImageUrl("https://cdn.example.com/selector-1.jpg")
                .build());

        persistItem(group(selector.getId(), 3L, (short) 1, purchasedAt.minusDays(3)),
                product, purchasedAt.minusDays(2));
        persistItem(group(selector.getId(), 3L, (short) 2, purchasedAt.minusDays(2)),
                product, purchasedAt.minusDays(1));
        persistItem(group(selector.getId(), 4L, (short) 3, purchasedAt.minusDays(3)),
                otherProduct, purchasedAt.minusDays(2));
        persistItem(group(accountlessSelector.getId(), 3L, (short) 1,
                        purchasedAt.minusDays(3)),
                product, purchasedAt.minusDays(2));
        persistItem(group(lateSelector.getId(), 3L, (short) 1, purchasedAt.plusHours(1)),
                product, purchasedAt.plusHours(2));
        persistItem(group(selfPurchasingSelector.getId(), 3L, (short) 1,
                        purchasedAt.minusDays(3)),
                product, purchasedAt.minusDays(2));
        persistItem(group(deletedSelector.getId(), 3L, (short) 1,
                        purchasedAt.minusDays(3)),
                product, purchasedAt.minusDays(2));

        PurchaseHistory confirmed = entityManager.persist(purchase(
                selector.getId(), product.getId(), "ORD-1", purchasedAt, "100"));
        confirmed.transitionTo(PurchaseStatus.PURCHASE_CONFIRMED, purchasedAt.plusDays(7));
        PurchaseHistory accountless = entityManager.persist(purchase(
                accountlessSelector.getId(), product.getId(), "ORD-NO-PROFILE",
                purchasedAt.plusSeconds(30), "90"));
        accountless.transitionTo(PurchaseStatus.PURCHASE_CONFIRMED, purchasedAt.plusDays(7));
        PurchaseHistory returned = entityManager.persist(purchase(
                selector.getId(), product.getId(), "ORD-2", purchasedAt.plusMinutes(1), "80"));
        returned.transitionTo(PurchaseStatus.PURCHASE_CONFIRMED, purchasedAt.plusDays(7));
        returned.transitionTo(PurchaseStatus.RETURN_REQUESTED, null);
        returned.transitionTo(PurchaseStatus.RETURNED, null);
        entityManager.persist(purchase(
                selector.getId(), product.getId(), "ORD-WAIT", purchasedAt.plusMinutes(2), "70"));
        PurchaseHistory wrongCampaign = entityManager.persist(purchase(
                selector.getId(), otherProduct.getId(), "ORD-OTHER", purchasedAt, "60"));
        wrongCampaign.transitionTo(PurchaseStatus.PURCHASE_CONFIRMED, purchasedAt.plusDays(7));
        PurchaseHistory groupCreatedLate = entityManager.persist(purchase(
                lateSelector.getId(), product.getId(), "ORD-LATE", purchasedAt, "50"));
        groupCreatedLate.transitionTo(PurchaseStatus.PURCHASE_CONFIRMED, purchasedAt.plusDays(7));
        PurchaseHistory selfPurchase = entityManager.persist(purchase(
                selfPurchasingSelector.getId(), product.getId(), "ORD-SELF",
                purchasedAt.plusMinutes(3), "40"));
        selfPurchase.transitionTo(PurchaseStatus.PURCHASE_CONFIRMED, purchasedAt.plusDays(7));
        PurchaseHistory deletedSelectorPurchase = entityManager.persist(purchase(
                deletedSelector.getId(), product.getId(), "ORD-DELETED",
                purchasedAt.plusMinutes(4), "30"));
        deletedSelectorPurchase.transitionTo(
                PurchaseStatus.PURCHASE_CONFIRMED, purchasedAt.plusDays(7));
        entityManager.flush();
        entityManager.clear();

        var result = repository.findAttributedTerminalPurchases(
                3L,
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 9, 1, 0, 0));

        assertThat(result).extracting(item -> item.orderNo())
                .containsExactly("ORD-1", "ORD-NO-PROFILE", "ORD-2");
        assertThat(result.getFirst().paidAmount()).isEqualByComparingTo("100");
        assertThat(result.getFirst().selectorCode()).isEqualTo("SEL-1");
        assertThat(result.getFirst().selectorProfileImageUrl())
                .isEqualTo("https://cdn.example.com/selector-1.jpg");
        assertThat(result.get(1).selectorProfileImageUrl()).isNull();
        assertThat(result.getFirst().productCode()).isEqualTo("P-1");
        assertThat(result.getLast().status()).isEqualTo(PurchaseStatus.RETURNED);
    }

    private void persistItem(ProductGroup group, Product product, LocalDateTime createdAt) {
        entityManager.persist(group);
        ProductGroupItem item = new ProductGroupItem(group, product, (short) 1);
        ReflectionTestUtils.setField(item, "createdAt", createdAt);
        entityManager.persist(item);
    }

    private ProductGroup group(
            Long selectorId, Long campaignId, short groupNo, LocalDateTime createdAt) {
        ProductGroup group = new ProductGroup(selectorId, campaignId, groupNo, "그룹");
        ReflectionTestUtils.setField(group, "createdAt", createdAt);
        return group;
    }

    private Selectors selector(String code, String nickname) {
        return selector(code, nickname, null);
    }

    private Selectors selector(String code, String nickname, Long userId) {
        return Selectors.builder()
                .userId(userId)
                .selectorsRoleId("ACTIVE")
                .selectorsCode(code)
                .selectorsNickname(nickname)
                .build();
    }

    private Product product(String code) {
        return Product.builder()
                .productCode(code)
                .productName("상품 " + code)
                .brandName("브랜드")
                .category("카테고리")
                .regularPrice(new BigDecimal("120"))
                .salePrice(new BigDecimal("100"))
                .status(ProductStatus.ON_SALE)
                .build();
    }

    private PurchaseHistory purchase(
            Long selectorId, Long productId, String orderNo,
            LocalDateTime purchasedAt, String amount) {
        BigDecimal paidAmount = new BigDecimal(amount);
        return PurchaseHistory.builder()
                .orderNo(orderNo)
                .userId(999L)
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
