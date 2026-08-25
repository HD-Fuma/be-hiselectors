package com.fuma.hiselectors.settlement.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.config.CacheConfig;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(CacheConfig.class)
class SettlementHistoryRepositoryTest {

    @Autowired
    private SettlementHistoryRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void excludesSettlementHistoryFromSearchAndSummaryWhenSelectorsRowIsMissing() {
        Selectors selectors = entityManager.persist(Selectors.builder()
                .userId(101L)
                .selectorsRoleId(Selectors.ACTIVE_ROLE)
                .selectorsCode("SEL-0101")
                .selectorsNickname("정산셀렉터")
                .build());
        entityManager.persist(history(selectors.getId()));
        entityManager.persist(history(999_999L));
        entityManager.flush();
        entityManager.clear();

        Page<SettlementHistory> result = repository.search(
                202607, null, null, PageRequest.of(0, 20));
        List<SettlementHistoryRepository.SettlementAggregate> summaries =
                repository.summarizeByMonthAndStatus(202602, 202607, null, null);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getSelectorsId())
                .isEqualTo(selectors.getId());
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(summaries).singleElement().satisfies(summary -> {
            assertThat(summary.getActivityYearMonth()).isEqualTo(202607);
            assertThat(summary.getStatus()).isEqualTo(SettlementStatus.CALCULATING);
            assertThat(summary.getSettlementCount()).isEqualTo(1);
            assertThat(summary.getConfirmedPurchaseCount()).isEqualTo(2);
            assertThat(summary.getConfirmedSalesAmount()).isEqualTo(10_000);
            assertThat(summary.getSettlementAmount()).isEqualTo(300);
        });
    }

    @Test
    void summarizesMonthAndStatusWithinSelectorsAndStatusFilters() {
        Selectors first = entityManager.persist(Selectors.builder()
                .userId(201L)
                .selectorsRoleId(Selectors.ACTIVE_ROLE)
                .selectorsCode("SEL-0201")
                .selectorsNickname("첫 셀렉터")
                .build());
        Selectors second = entityManager.persist(Selectors.builder()
                .userId(202L)
                .selectorsRoleId(Selectors.ACTIVE_ROLE)
                .selectorsCode("SEL-0202")
                .selectorsNickname("둘째 셀렉터")
                .build());
        entityManager.persist(history(
                first.getId(), YearMonth.of(2026, 6), 20_000L, 3L, 600L,
                SettlementStatus.CALCULATING));
        entityManager.persist(history(
                first.getId(), YearMonth.of(2026, 7), 30_000L, 4L, 900L,
                SettlementStatus.SETTLED));
        entityManager.persist(history(
                second.getId(), YearMonth.of(2026, 7), 40_000L, 5L, 1_200L,
                SettlementStatus.SETTLED));
        entityManager.flush();
        entityManager.clear();

        List<SettlementHistoryRepository.SettlementAggregate> summaries =
                repository.summarizeByMonthAndStatus(
                        202606, 202607, first.getId(), SettlementStatus.SETTLED);

        assertThat(summaries).singleElement().satisfies(summary -> {
            assertThat(summary.getActivityYearMonth()).isEqualTo(202607);
            assertThat(summary.getStatus()).isEqualTo(SettlementStatus.SETTLED);
            assertThat(summary.getSettlementCount()).isEqualTo(1);
            assertThat(summary.getConfirmedPurchaseCount()).isEqualTo(4);
            assertThat(summary.getConfirmedSalesAmount()).isEqualTo(30_000);
            assertThat(summary.getSettlementAmount()).isEqualTo(900);
        });
    }

    private SettlementHistory history(Long selectorsId) {
        return history(
                selectorsId, YearMonth.of(2026, 7), 10_000L, 2L, 300L,
                SettlementStatus.CALCULATING);
    }

    private SettlementHistory history(
            Long selectorsId,
            YearMonth activityMonth,
            long salesAmount,
            long purchaseCount,
            long settlementAmount,
            SettlementStatus status) {
        LocalDateTime calculatedAt = activityMonth.plusMonths(1).atDay(1).atTime(3, 0);
        SettlementHistory history = SettlementHistory.create(
                selectorsId, activityMonth.atDay(1).atStartOfDay());
        history.updateCalculation(
                salesAmount, purchaseCount, new BigDecimal("3.00"), settlementAmount,
                calculatedAt);
        if (status != SettlementStatus.CALCULATING) {
            history.transitionTo(status, calculatedAt.plusDays(1));
        }
        return history;
    }
}
