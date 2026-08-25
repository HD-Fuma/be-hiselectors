package com.fuma.hiselectors.selectors.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.config.CacheConfig;
import com.fuma.hiselectors.config.JpaAuditingConfig;
import com.fuma.hiselectors.penalty.model.PenaltyHistory;
import com.fuma.hiselectors.penalty.repository.PenaltyHistoryRepository;
import com.fuma.hiselectors.selectors.model.BlacklistHistory;
import com.fuma.hiselectors.selectors.model.Selectors;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({CacheConfig.class, JpaAuditingConfig.class})
class SelectorsPenaltyRepositoryTest {

    @Autowired
    private SelectorsRepository selectorsRepository;

    @Autowired
    private PenaltyHistoryRepository penaltyHistoryRepository;

    @Autowired
    private BlacklistHistoryRepository blacklistHistoryRepository;

    @Test
    void switchesBetweenPersistedBlacklistAndPenaltyHistory() {
        Selectors blacklistedWithoutHistory = selectorsRepository.save(Selectors.builder()
                .userId(1L)
                .selectorsRoleId(Selectors.BLACKLIST_ROLE)
                .selectorsCode("SEL001")
                .selectorsNickname("blacklisted")
                .build());
        Selectors activeWithHistory = selectorsRepository.save(Selectors.builder()
                .userId(2L)
                .selectorsRoleId(Selectors.ACTIVE_ROLE)
                .selectorsCode("SEL002")
                .selectorsNickname("penalized")
                .build());
        penaltyHistoryRepository.saveAndFlush(PenaltyHistory.activate(
                activeWithHistory.getId(), 1L, LocalDateTime.of(2026, 8, 23, 12, 0)));
        PageRequest page = PageRequest.of(0, 20);

        assertThat(selectorsRepository.searchWithPenalties(null, null, true, page).getContent())
                .containsExactly(blacklistedWithoutHistory);
        assertThat(selectorsRepository.searchWithPenalties(null, null, false, page).getContent())
                .containsExactly(activeWithHistory);
    }

    @Test
    void savesBlacklistHistoryUsingExistingTableColumns() {
        Selectors selectors = selectorsRepository.save(Selectors.builder()
                .userId(3L)
                .selectorsRoleId(Selectors.BLACKLIST_ROLE)
                .selectorsCode("SEL003")
                .selectorsNickname("history")
                .build());

        BlacklistHistory saved = blacklistHistoryRepository.saveAndFlush(
                BlacklistHistory.activate(selectors.getId(), "패널티 누적 3회"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSelectorsId()).isEqualTo(selectors.getId());
        assertThat(saved.getReason()).isEqualTo("패널티 누적 3회");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
