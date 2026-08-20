package com.fuma.hiselectors.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.service.SelectorAccessService;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettlementEstimateServiceTest {

    @Test
    void getsOnlyTheAuthenticatedSelectorsHistoriesForTheRequestedYear() {
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SelectorAccessService selectorAccessService = mock(SelectorAccessService.class);
        Selectors selectors = mock(Selectors.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-16T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        SettlementEstimateService service = new SettlementEstimateService(
                historyRepository, clock, selectorAccessService);

        when(selectors.getId()).thenReturn(9L);
        when(selectorAccessService.requireSettlementHistoryReadable("selector-user"))
                .thenReturn(selectors);
        when(historyRepository
                .findAllBySelectorsIdAndActivityMonthGreaterThanEqualAndActivityMonthLessThanOrderByActivityMonthDesc(
                        9L, LocalDateTime.of(2025, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0)))
                .thenReturn(List.of());
        when(historyRepository.findAvailableYearsBySelectorsId(9L)).thenReturn(List.of(2026, 2025));

        var result = service.getHistories("selector-user", 2025);

        assertThat(result.selectedYear()).isEqualTo(2025);
        assertThat(result.availableYears()).containsExactly(2026, 2025);
        assertThat(result.histories()).isEmpty();
        verify(historyRepository)
                .findAllBySelectorsIdAndActivityMonthGreaterThanEqualAndActivityMonthLessThanOrderByActivityMonthDesc(
                        9L, LocalDateTime.of(2025, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 1, 0, 0));
    }
}
