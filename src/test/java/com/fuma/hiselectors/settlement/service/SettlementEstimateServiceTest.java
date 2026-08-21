package com.fuma.hiselectors.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.service.SelectorAccessService;
import com.fuma.hiselectors.settlement.dto.SettlementProvisionalEstimate;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettlementEstimateServiceTest {

    @Test
    void getsCurrentMonthByDefaultWithLiveProvisionalEstimate() {
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SelectorAccessService selectorAccessService = mock(SelectorAccessService.class);
        SettlementProvisionalEstimateService provisionalService =
                mock(SettlementProvisionalEstimateService.class);
        Selectors selectors = mock(Selectors.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-16T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        SettlementEstimateService service = new SettlementEstimateService(
                historyRepository, clock, selectorAccessService, provisionalService);
        SettlementHistory history = SettlementHistory.create(
                9L, LocalDateTime.of(2026, 8, 1, 0, 0));
        history.updateCalculation(
                10_000L, 2L, new BigDecimal("3.00"), 300L,
                LocalDateTime.of(2026, 8, 16, 3, 0));
        SettlementProvisionalEstimate provisional = new SettlementProvisionalEstimate(
                3L, 12_000L, 360L, LocalDateTime.of(2026, 8, 16, 9, 0));

        when(selectors.getId()).thenReturn(9L);
        when(selectorAccessService.requireCurrent("selector-user")).thenReturn(selectors);
        when(historyRepository.findBySelectorsIdAndActivityYearMonth(9L, 202608))
                .thenReturn(Optional.of(history));
        when(provisionalService.calculate(history)).thenReturn(provisional);

        var result = service.getEstimate("selector-user", null);

        assertThat(result.activityMonth()).isEqualTo(java.time.YearMonth.of(2026, 8));
        assertThat(result.provisionalEstimate()).isEqualTo(provisional);
        verify(provisionalService).calculate(history);
    }

    @Test
    void getsOnlyTheAuthenticatedSelectorsHistoriesForTheRequestedYear() {
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SelectorAccessService selectorAccessService = mock(SelectorAccessService.class);
        Selectors selectors = mock(Selectors.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-16T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        SettlementEstimateService service = new SettlementEstimateService(
                historyRepository, clock, selectorAccessService,
                mock(SettlementProvisionalEstimateService.class));

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
