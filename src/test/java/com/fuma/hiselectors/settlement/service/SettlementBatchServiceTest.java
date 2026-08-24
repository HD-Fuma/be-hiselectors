package com.fuma.hiselectors.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import com.fuma.hiselectors.taskrun.service.TaskProgressReporter;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class SettlementBatchServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Test
    void calculatesPreviousAndCurrentActivityMonthsEveryDay() {
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        SettlementCalculationWorker worker = mock(SettlementCalculationWorker.class);
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        TaskProgressReporter progress = mock(TaskProgressReporter.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-10T18:00:00Z"), SEOUL);
        SettlementBatchService service = new SettlementBatchService(
                selectorsRepository, worker, historyRepository,
                new SettlementSchedulePolicy(), clock);
        when(selectorsRepository.findAllIds()).thenReturn(List.of(1L));
        when(worker.calculate(1L, YearMonth.of(2026, 7), false)).thenReturn(
                new SettlementCalculationResult(mock(SettlementHistory.class),
                        SettlementCalculationOutcome.UPDATED));
        when(worker.calculate(1L, YearMonth.of(2026, 8), false)).thenReturn(
                new SettlementCalculationResult(mock(SettlementHistory.class),
                        SettlementCalculationOutcome.SKIPPED));

        SettlementBatchService.SettlementBatchResult result =
                service.calculateOpenActivityMonth(progress);

        assertThat(result.activityMonth()).isEqualTo(YearMonth.of(2026, 8));
        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        InOrder order = inOrder(progress, worker);
        order.verify(progress).start("ESTIMATE", 2);
        order.verify(worker).calculate(1L, YearMonth.of(2026, 7), false);
        order.verify(progress).advance(1, 0, 0);
        order.verify(worker).calculate(1L, YearMonth.of(2026, 8), false);
        order.verify(progress).advance(0, 0, 1);
    }

    @Test
    void finalizesOnTheTwentyFirstBusinessDayAndContinuesAfterIndividualFailure() {
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        SettlementCalculationWorker worker = mock(SettlementCalculationWorker.class);
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        TaskProgressReporter progress = mock(TaskProgressReporter.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T15:00:00Z"), SEOUL);
        SettlementBatchService service =
                new SettlementBatchService(selectorsRepository, worker, historyRepository,
                        new SettlementSchedulePolicy(), clock);
        when(selectorsRepository.findAllIds()).thenReturn(List.of(1L, 2L));
        SettlementHistory duplicate = mock(SettlementHistory.class);
        when(duplicate.getSelectorsId()).thenReturn(1L);
        when(duplicate.getActivityMonth()).thenReturn(LocalDateTime.of(2026, 7, 1, 0, 0));
        when(historyRepository
                .findAllByStatusAndActivityYearMonthLessThanEqualOrderByActivityYearMonthAsc(
                        SettlementStatus.CALCULATING, 202607))
                .thenReturn(List.of(duplicate));
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
                service.finalizeOpenActivityMonth(progress);

        assertThat(result.finalized()).isTrue();
        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        InOrder order = inOrder(progress, worker);
        order.verify(progress).start("FINALIZE", 2);
        order.verify(worker).calculate(1L, YearMonth.of(2026, 7), true);
        order.verify(progress).advance(0, 1, 0);
        order.verify(worker).calculate(2L, YearMonth.of(2026, 7), true);
        order.verify(progress).advance(1, 0, 0);
    }

    @Test
    void retriesOverdueCalculatingHistoryAfterTheActivityMonthChanges() {
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        SettlementCalculationWorker worker = mock(SettlementCalculationWorker.class);
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        TaskProgressReporter progress = mock(TaskProgressReporter.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), SEOUL);
        SettlementBatchService service = new SettlementBatchService(
                selectorsRepository, worker, historyRepository, new SettlementSchedulePolicy(), clock);
        SettlementHistory overdueHistory = mock(SettlementHistory.class);
        when(overdueHistory.getSelectorsId()).thenReturn(7L);
        when(overdueHistory.getActivityMonth()).thenReturn(LocalDateTime.of(2026, 7, 1, 0, 0));
        when(historyRepository
                .findAllByStatusAndActivityYearMonthLessThanEqualOrderByActivityYearMonthAsc(
                        SettlementStatus.CALCULATING, 202608))
                .thenReturn(List.of(overdueHistory));
        when(worker.calculate(7L, YearMonth.of(2026, 7), true)).thenReturn(
                new SettlementCalculationResult(mock(SettlementHistory.class),
                        SettlementCalculationOutcome.FINALIZED));

        SettlementBatchService.SettlementBatchResult result =
                service.finalizeOpenActivityMonth(progress);

        assertThat(result.finalized()).isTrue();
        assertThat(result.processedCount()).isEqualTo(1);
        verify(worker).calculate(7L, YearMonth.of(2026, 7), true);
        verify(progress).start("FINALIZE", 1);
        verify(progress).advance(1, 0, 0);
    }

    @Test
    void finalizationWithNoTargetsReportsZeroAndSucceedsWithoutWorkers() {
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        SettlementCalculationWorker worker = mock(SettlementCalculationWorker.class);
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        TaskProgressReporter progress = mock(TaskProgressReporter.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-09T15:00:00Z"), SEOUL);
        SettlementBatchService service = new SettlementBatchService(
                selectorsRepository, worker, historyRepository,
                new SettlementSchedulePolicy(), clock);
        when(historyRepository
                .findAllByStatusAndActivityYearMonthLessThanEqualOrderByActivityYearMonthAsc(
                        SettlementStatus.CALCULATING, 202607))
                .thenReturn(List.of());

        SettlementBatchService.SettlementBatchResult result =
                service.finalizeOpenActivityMonth(progress);

        assertThat(result.finalized()).isFalse();
        assertThat(result.processedCount() + result.skippedCount() + result.failedCount())
                .isZero();
        verify(progress).start("FINALIZE", 0);
        verifyNoInteractions(worker);
    }

    @Test
    void doesNotMisclassifyProgressFailureAsWorkerFailure() {
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        SettlementCalculationWorker worker = mock(SettlementCalculationWorker.class);
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        TaskProgressReporter progress = mock(TaskProgressReporter.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-10T18:00:00Z"), SEOUL);
        SettlementBatchService service = new SettlementBatchService(
                selectorsRepository, worker, historyRepository,
                new SettlementSchedulePolicy(), clock);
        when(selectorsRepository.findAllIds()).thenReturn(List.of(1L));
        when(worker.calculate(1L, YearMonth.of(2026, 7), false)).thenReturn(
                new SettlementCalculationResult(mock(SettlementHistory.class),
                        SettlementCalculationOutcome.UPDATED));
        IllegalStateException progressFailure = new IllegalStateException("lease lost");
        org.mockito.Mockito.doThrow(progressFailure).when(progress).advance(1, 0, 0);

        assertThatThrownBy(() -> service.calculateOpenActivityMonth(progress))
                .isSameAs(progressFailure);

        verify(progress, never()).advance(0, 1, 0);
        verify(worker, never()).calculate(1L, YearMonth.of(2026, 8), false);
    }
}
