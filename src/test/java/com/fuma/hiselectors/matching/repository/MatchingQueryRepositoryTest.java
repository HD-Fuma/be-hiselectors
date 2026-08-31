package com.fuma.hiselectors.matching.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.matching.repository.MatchingQueryRepository.CategorySales;
import com.fuma.hiselectors.product.model.Product;
import com.fuma.hiselectors.product.model.ProductStatus;
import com.fuma.hiselectors.purchase.model.PurchaseHistory;
import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.selectors.model.Selectors;
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
@Import({MatchingQueryRepository.class, MatchingQueryRepositoryTest.CacheConfig.class})
class MatchingQueryRepositoryTest {

    @Autowired
    private MatchingQueryRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void ranksCategoryConfirmedSalesExcludingSelfDeletedBlacklistedAndOtherCategories() {
        Selectors seller = entityManager.persist(selector(101L, "SEL-A", "판매왕", "ACTIVE"));
        seller.assignCategory("BEAUTY");
        Selectors rising = entityManager.persist(selector(102L, "SEL-B", "신인", "ACTIVE"));
        Selectors deleted = entityManager.persist(selector(103L, "SEL-D", "삭제", "ACTIVE"));
        deleted.softDelete();
        Selectors blacklisted = entityManager.persist(selector(104L, "SEL-X", "블랙", "BLACKLIST"));

        Long beauty1 = entityManager.persist(product("P-B1", "BEAUTY")).getId();
        Long beauty2 = entityManager.persist(product("P-B2", "BEAUTY")).getId();
        Long food = entityManager.persist(product("P-F1", "FOOD")).getId();

        entityManager.persist(confirmed(seller.getId(), 201L, beauty1, "ORD-A1", "100"));
        entityManager.persist(confirmed(seller.getId(), 202L, beauty2, "ORD-A2", "150"));
        entityManager.persist(confirmed(seller.getId(), 101L, beauty1, "ORD-SELF", "999"));
        entityManager.persist(confirmed(seller.getId(), 203L, food, "ORD-FOOD", "500"));
        entityManager.persist(confirmed(rising.getId(), 204L, beauty1, "ORD-B1", "300"));
        entityManager.persist(confirmed(deleted.getId(), 205L, beauty1, "ORD-D1", "400"));
        entityManager.persist(confirmed(blacklisted.getId(), 206L, beauty1, "ORD-X1", "400"));
        entityManager.flush();
        entityManager.clear();

        List<CategorySales> ranking =
                repository.summarizeCategoryConfirmedSales(List.of("BEAUTY"), null, null);

        assertThat(ranking).extracting(CategorySales::selectorId)
                .containsExactly(rising.getId(), seller.getId());
        assertThat(ranking).filteredOn(row -> row.selectorId().equals(seller.getId()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.totalSales()).isEqualByComparingTo("250");
                    assertThat(row.confirmedOrderCount()).isEqualTo(2L);
                });

        assertThat(repository.findRepresentativeCategorySelectors(List.of("BEAUTY")))
                .extracting(Selectors::getId)
                .containsExactly(seller.getId());
    }

    private Selectors selector(Long userId, String code, String nickname, String role) {
        return Selectors.builder()
                .userId(userId)
                .selectorsRoleId(role)
                .selectorsCode(code)
                .selectorsNickname(nickname)
                .build();
    }

    private Product product(String code, String category) {
        return Product.builder()
                .productCode(code)
                .productName(code)
                .category(category)
                .regularPrice(new BigDecimal("1000"))
                .salePrice(new BigDecimal("900"))
                .status(ProductStatus.ON_SALE)
                .build();
    }

    private PurchaseHistory confirmed(
            Long selectorId, Long userId, Long productId, String orderNo, String amount) {
        BigDecimal paid = new BigDecimal(amount);
        PurchaseHistory purchase = PurchaseHistory.builder()
                .orderNo(orderNo)
                .userId(userId)
                .selectorsId(selectorId)
                .productId(productId)
                .quantity(1)
                .regularUnitPrice(paid)
                .saleUnitPrice(paid)
                .discountAmount(BigDecimal.ZERO)
                .paidAmount(paid)
                .purchasedAt(LocalDateTime.of(2026, 8, 1, 0, 0))
                .build();
        purchase.transitionTo(PurchaseStatus.PURCHASE_CONFIRMED, LocalDateTime.of(2026, 8, 10, 10, 0));
        return purchase;
    }

    @TestConfiguration
    static class CacheConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }
}
