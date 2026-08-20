package com.fuma.hiselectors.selectors.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.penalty.model.PenaltyHistory;
import com.fuma.hiselectors.penalty.model.PenaltyStatus;
import com.fuma.hiselectors.penalty.repository.PenaltyHistoryRepository;
import com.fuma.hiselectors.selectors.dto.SelectorsPenaltyResponse;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsGenerationRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsRoleRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class SelectorsServiceTest {

    private SelectorsRepository selectorsRepository;
    private PenaltyHistoryRepository penaltyHistoryRepository;
    private SelectorsService selectorsService;

    @BeforeEach
    void setUp() {
        selectorsRepository = mock(SelectorsRepository.class);
        penaltyHistoryRepository = mock(PenaltyHistoryRepository.class);
        selectorsService = new SelectorsService(
                selectorsRepository,
                mock(SelectorsRoleRepository.class),
                mock(SelectorsGenerationRepository.class),
                mock(SelectorsSnsAccountRepository.class),
                penaltyHistoryRepository);
    }

    @Test
    void returnsPenaltyCountsAndBlacklistTarget() {
        Pageable pageable = PageRequest.of(0, 20);
        Selectors selectors = mock(Selectors.class);
        when(selectors.getId()).thenReturn(1L);
        when(selectors.getSelectorsCode()).thenReturn("SEL001");
        when(selectors.getSelectorsNickname()).thenReturn("tester");
        when(selectorsRepository.searchWithPenalties(
                2L, PenaltyStatus.ACTIVE, true, 3L, pageable))
                .thenReturn(new PageImpl<>(List.of(selectors), pageable, 1));

        LocalDateTime now = LocalDateTime.now();
        PenaltyHistory released = PenaltyHistory.activate(1L, 10L, now.minusDays(3));
        released.release(now.minusDays(2));
        when(penaltyHistoryRepository.findAllBySelectorsIds(List.of(1L)))
                .thenReturn(List.of(
                        PenaltyHistory.activate(1L, 11L, now.minusDays(1)),
                        PenaltyHistory.activate(1L, 12L, now),
                        released));

        Page<SelectorsPenaltyResponse> result = selectorsService.findPenalties(
                2L, PenaltyStatus.ACTIVE, true, pageable);

        assertThat(result.getContent()).singleElement().satisfies(response -> {
            assertThat(response.totalPenaltyCount()).isEqualTo(3);
            assertThat(response.activePenaltyCount()).isEqualTo(2);
            assertThat(response.blacklistTarget()).isTrue();
            assertThat(response.histories()).hasSize(3);
        });
        verify(selectorsRepository).searchWithPenalties(
                2L, PenaltyStatus.ACTIVE, true, 3L, pageable);
    }
}
