package com.fuma.hiselectors.selectors.excellence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.model.GenerationStatus;
import com.fuma.hiselectors.purchase.model.PurchaseHistory;
import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.selectors.excellence.repository.SelectorExcellenceQueryRepository.SalesCandidate;
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
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({SelectorExcellenceQueryRepository.class,
        SelectorExcellenceQueryRepositoryTest.CacheConfig.class})
class SelectorExcellenceQueryRepositoryTest {

    private static final LocalDateTime ACTIVITY_START =
            LocalDateTime.of(2026, 7, 1, 9, 0);
    private static final LocalDateTime ACTIVITY_END_EXCLUSIVE =
            LocalDateTime.of(2026, 8, 11, 0, 0);
    private static final LocalDateTime AS_OF =
            LocalDateTime.of(2026, 8, 17, 0, 0);

    @Autowired
    private SelectorExcellenceQueryRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void aggregatesOnlyEligibleGenerationPurchasesUsingPurchaseAndConfirmationBoundaries() {
        Generation generation = entityManager.persist(generation());
        Selectors first = entityManager.persist(selector(101L, Selectors.ACTIVE_ROLE));
        Selectors second = entityManager.persist(selector(102L, Selectors.ACTIVE_ROLE));
        Selectors blacklisted = entityManager.persist(
                selector(103L, Selectors.BLACKLIST_ROLE));
        Selectors deleted = entityManager.persist(selector(104L, Selectors.ACTIVE_ROLE));
        deleted.softDelete();
        Selectors zeroSales = entityManager.persist(selector(105L, Selectors.ACTIVE_ROLE));

        LocalDateTime joinedAt = LocalDateTime.of(2026, 7, 5, 10, 0);
        SelectorsGeneration firstMembership = membership(
                first.getId(), generation.getId(), joinedAt);
        SelectorsGeneration legacyMembership = membership(
                second.getId(), generation.getId(), ACTIVITY_START);
        membership(blacklisted.getId(), generation.getId(), ACTIVITY_START);
        membership(deleted.getId(), generation.getId(), ACTIVITY_START);
        membership(zeroSales.getId(), generation.getId(), ACTIVITY_START);

        persistConfirmed(first, 201L, 1L, "ORD-1", "100", joinedAt, AS_OF.minusDays(1));
        persistConfirmed(first, 202L, 2L, "ORD-1", "150", joinedAt.plusHours(1), AS_OF);
        persistConfirmed(first, 203L, 3L, "ORD-2", "50", joinedAt.plusDays(1), AS_OF);
        persistConfirmed(first, 204L, 4L, "BEFORE-MEMBER", "999",
                joinedAt.minusSeconds(1), AS_OF);
        persistConfirmed(first, 205L, 5L, "END-BOUNDARY", "999",
                ACTIVITY_END_EXCLUSIVE, AS_OF);
        persistConfirmed(first, first.getUserId(), 6L, "SELF", "999",
                joinedAt.plusDays(1), AS_OF);
        persistConfirmed(first, 206L, 7L, "LATE-CONFIRM", "999",
                joinedAt.plusDays(1), AS_OF.plusSeconds(1));
        persistReturned(first, 207L, 8L, "RETURNED", "999",
                joinedAt.plusDays(1), AS_OF.minusDays(1));
        entityManager.persist(purchase(
                first, 208L, 9L, "NOT-CONFIRMED", "999", joinedAt.plusDays(1)));

        persistConfirmed(second, 209L, 10L, "LEGACY", "300",
                ACTIVITY_START, AS_OF);
        persistConfirmed(blacklisted, 210L, 11L, "BLACK", "10000",
                ACTIVITY_START, AS_OF);
        persistConfirmed(deleted, 211L, 12L, "DELETED", "10000",
                ACTIVITY_START, AS_OF);
        persistConfirmed(zeroSales, 212L, 13L, "ZERO-SALES", "0",
                ACTIVITY_START, AS_OF);

        entityManager.flush();
        entityManager.getEntityManager().createNativeQuery("""
                        update selectors_generation
                        set created_at = :joinedAt
                        where selectors_generation_id = :membershipId
                        """)
                .setParameter("joinedAt", joinedAt)
                .setParameter("membershipId", firstMembership.getId())
                .executeUpdate();
        entityManager.getEntityManager().createNativeQuery("""
                        update selectors_generation
                        set created_at = null
                        where selectors_generation_id = :membershipId
                        """)
                .setParameter("membershipId", legacyMembership.getId())
                .executeUpdate();
        entityManager.clear();

        List<SalesCandidate> result = repository.findSalesCandidates(
                generation.getId(), ACTIVITY_START, ACTIVITY_END_EXCLUSIVE, AS_OF);

        assertThat(repository.hasPendingPurchases(
                generation.getId(), ACTIVITY_START, ACTIVITY_END_EXCLUSIVE)).isTrue();

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(SalesCandidate::selectorsId)
                .containsExactly(first.getId(), second.getId());
        assertThat(result.get(0).generationSales()).isEqualByComparingTo("300");
        assertThat(result.get(0).confirmedOrderCount()).isEqualTo(2L);
        assertThat(result.get(1).generationSales()).isEqualByComparingTo("300");
        assertThat(result.get(1).confirmedOrderCount()).isEqualTo(1L);
    }

    private Generation generation() {
        return Generation.builder()
                .generationName("7기")
                .startDate(ACTIVITY_START.minusMonths(1))
                .endDate(ACTIVITY_START.minusDays(1))
                .activityStartDate(ACTIVITY_START)
                .activityEndDate(LocalDateTime.of(2026, 8, 10, 23, 59, 59))
                .status(GenerationStatus.INACTIVE)
                .build();
    }

    private Selectors selector(Long userId, String role) {
        return Selectors.builder()
                .userId(userId)
                .selectorsRoleId(role)
                .selectorsCode("SEL-" + userId)
                .selectorsNickname("selector-" + userId)
                .build();
    }

    private SelectorsGeneration membership(
            Long selectorsId, Long generationId, LocalDateTime createdAt) {
        SelectorsGeneration membership = SelectorsGeneration.builder()
                .selectorsId(selectorsId)
                .generationId(generationId)
                .build();
        ReflectionTestUtils.setField(membership, "createdAt", createdAt);
        return entityManager.persist(membership);
    }

    private void persistConfirmed(
            Selectors selectors,
            Long buyerId,
            Long productId,
            String orderNo,
            String amount,
            LocalDateTime purchasedAt,
            LocalDateTime confirmedAt) {
        PurchaseHistory purchase = purchase(
                selectors, buyerId, productId, orderNo, amount, purchasedAt);
        purchase.transitionTo(PurchaseStatus.PURCHASE_CONFIRMED, confirmedAt);
        entityManager.persist(purchase);
    }

    private void persistReturned(
            Selectors selectors,
            Long buyerId,
            Long productId,
            String orderNo,
            String amount,
            LocalDateTime purchasedAt,
            LocalDateTime confirmedAt) {
        PurchaseHistory purchase = purchase(
                selectors, buyerId, productId, orderNo, amount, purchasedAt);
        purchase.transitionTo(PurchaseStatus.PURCHASE_CONFIRMED, confirmedAt);
        purchase.transitionTo(PurchaseStatus.RETURNED, null);
        entityManager.persist(purchase);
    }

    private PurchaseHistory purchase(
            Selectors selectors,
            Long buyerId,
            Long productId,
            String orderNo,
            String amount,
            LocalDateTime purchasedAt) {
        BigDecimal paidAmount = new BigDecimal(amount);
        return PurchaseHistory.builder()
                .orderNo(orderNo)
                .userId(buyerId)
                .selectorsId(selectors.getId())
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
