package com.fuma.hiselectors.settlement.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.config.CacheConfig;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    void excludesSettlementHistoryWhenSelectorsRowIsMissing() {
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

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getSelectorsId())
                .isEqualTo(selectors.getId());
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    private SettlementHistory history(Long selectorsId) {
        SettlementHistory history = SettlementHistory.create(
                selectorsId, LocalDateTime.of(2026, 7, 1, 0, 0));
        history.updateCalculation(
                10_000L, 2L, new BigDecimal("3.00"), 300L,
                LocalDateTime.of(2026, 8, 1, 3, 0));
        return history;
    }
}
