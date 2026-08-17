package com.fuma.hiselectors.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettlementBatchServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Test
    void finalizesOnTheTwentyFirstBusinessDayAndContinuesAfterIndividualFailure() {
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        SettlementCalculationWorker worker = mock(SettlementCalculationWorker.class);
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T15:00:00Z"), SEOUL);
        SettlementBatchService service =
                new SettlementBatchService(selectorsRepository, worker, historyRepository,
                        new SettlementSchedulePolicy(), clock);
        when(selectorsRepository.findAllIds()).thenReturn(List.of(1L, 2L));
        when(historyRepository.findAllByStatusAndActivityMonthLessThanEqualOrderByActivityMonthAsc(
                SettlementStatus.CALCULATING, LocalDateTime.of(2026, 7, 1, 0, 0)))
                .thenReturn(List.of());
        SettlementHistory history = mock(SettlementHistory.class);
        when(worker.calculate(
                eq(1L), eq(YearMonth.of(2026, 7)),
                eq(true)))
                .thenThrow(new IllegalStateException("broken row"));
        when(worker.calculate(
                eq(2L), eq(YearMonth.of(2026, 7)),
                eq(true)))
                .thenReturn(new SettlementCalculationResult(
                        history, SettlementCalculationOutcome.FINALIZED));

        SettlementBatchService.SettlementBatchResult result =
                service.finalizeOpenActivityMonth();

        assertThat(result.finalized()).isTrue();
        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        verify(worker).calculate(
                2L, YearMonth.of(2026, 7), true);
    }

    @Test
    void retriesOverdueCalculatingHistoryAfterTheActivityMonthChanges() {
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        SettlementCalculationWorker worker = mock(SettlementCalculationWorker.class);
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), SEOUL);
        SettlementBatchService service = new SettlementBatchService(
                selectorsRepository, worker, historyRepository, new SettlementSchedulePolicy(), clock);
        SettlementHistory overdueHistory = mock(SettlementHistory.class);
        when(overdueHistory.getSelectorsId()).thenReturn(7L);
        when(overdueHistory.getActivityMonth()).thenReturn(LocalDateTime.of(2026, 7, 1, 0, 0));
        when(historyRepository.findAllByStatusAndActivityMonthLessThanEqualOrderByActivityMonthAsc(
                SettlementStatus.CALCULATING, LocalDateTime.of(2026, 8, 1, 0, 0)))
                .thenReturn(List.of(overdueHistory));
        when(worker.calculate(7L, YearMonth.of(2026, 7), true)).thenReturn(
                new SettlementCalculationResult(mock(SettlementHistory.class),
                        SettlementCalculationOutcome.FINALIZED));

        SettlementBatchService.SettlementBatchResult result =
                service.finalizeOpenActivityMonth();

        assertThat(result.finalized()).isTrue();
        assertThat(result.processedCount()).isEqualTo(1);
        verify(worker).calculate(7L, YearMonth.of(2026, 7), true);
    }
}
