package com.fuma.hiselectors.selectors.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.config.CacheConfig;
import com.fuma.hiselectors.penalty.model.PenaltyHistory;
import com.fuma.hiselectors.penalty.repository.PenaltyHistoryRepository;
import com.fuma.hiselectors.selectors.model.Selectors;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(CacheConfig.class)
class SelectorsPenaltyRepositoryTest {

    @Autowired
    private SelectorsRepository selectorsRepository;

    @Autowired
    private PenaltyHistoryRepository penaltyHistoryRepository;

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
}
