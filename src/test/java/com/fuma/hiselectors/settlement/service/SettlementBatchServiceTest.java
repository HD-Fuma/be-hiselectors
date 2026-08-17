package com.fuma.hiselectors.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import java.time.Clock;
import java.time.Instant;
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
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T15:00:00Z"), SEOUL);
        SettlementBatchService service =
                new SettlementBatchService(selectorsRepository, worker,
                        new SettlementSchedulePolicy(), clock);
        when(selectorsRepository.findAllIds()).thenReturn(List.of(1L, 2L));
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
}
