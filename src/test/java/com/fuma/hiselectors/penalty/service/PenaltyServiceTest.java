package com.fuma.hiselectors.penalty.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.inspection.repository.ViolationItemRepository;
import com.fuma.hiselectors.penalty.model.PenaltyHistory;
import com.fuma.hiselectors.penalty.model.PenaltyStatus;
import com.fuma.hiselectors.penalty.repository.PenaltyHistoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PenaltyServiceTest {

    @Test
    void doesNotCreateAnotherActivePenalty() {
        PenaltyHistoryRepository historyRepository = mock(PenaltyHistoryRepository.class);
        ViolationItemRepository itemRepository = mock(ViolationItemRepository.class);
        PenaltyHistory active = PenaltyHistory.activate(1L, 10L,
                java.time.LocalDateTime.of(2026, 8, 18, 10, 0));
        when(historyRepository.findFirstBySelectorsIdAndStatusOrderByIdDesc(
                1L, PenaltyStatus.ACTIVE)).thenReturn(Optional.of(active));
        PenaltyService service = service(historyRepository, itemRepository);

        assertThat(service.activateIfAbsent(1L, 11L)).isFalse();
        verify(historyRepository, never()).save(any());
    }

    @Test
    void releasesOnlyWhenNoOpenViolationRemains() {
        PenaltyHistoryRepository historyRepository = mock(PenaltyHistoryRepository.class);
        ViolationItemRepository itemRepository = mock(ViolationItemRepository.class);
        PenaltyHistory active = PenaltyHistory.activate(1L, 10L,
                java.time.LocalDateTime.of(2026, 8, 18, 10, 0));
        when(itemRepository.existsOpenBySelectorsId(any(), any())).thenReturn(false);
        when(historyRepository.findFirstBySelectorsIdAndStatusOrderByIdDesc(
                1L, PenaltyStatus.ACTIVE)).thenReturn(Optional.of(active));
        PenaltyService service = service(historyRepository, itemRepository);

        assertThat(service.releaseIfEligible(1L)).isTrue();
        assertThat(active.getStatus()).isEqualTo(PenaltyStatus.RELEASED);
    }

    private PenaltyService service(PenaltyHistoryRepository historyRepository,
                                   ViolationItemRepository itemRepository) {
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T03:00:00Z"), ZoneOffset.UTC);
        return new PenaltyService(historyRepository, itemRepository, clock);
    }
}
