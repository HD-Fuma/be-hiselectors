package com.fuma.hiselectors.settlement.task;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.settlement.dto.SettlementRecalculationResponse;
import com.fuma.hiselectors.settlement.service.SettlementBatchService;
import com.fuma.hiselectors.settlement.service.SettlementBatchService.SettlementBatchResult;
import com.fuma.hiselectors.settlement.service.SettlementRecalculationService;
import com.fuma.hiselectors.taskrun.service.TaskExecutionContext;
import com.fuma.hiselectors.taskrun.service.TaskLease;
import com.fuma.hiselectors.taskrun.service.TaskProgressReporter;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class SettlementTaskTest {

    @Test
    void estimateShowsItsStepBeforeDelegatingProgressToTheBatch() {
        SettlementBatchService service = mock(SettlementBatchService.class);
        TaskProgressReporter progress = mock(TaskProgressReporter.class);
        when(service.calculateOpenActivityMonth(progress))
                .thenReturn(new SettlementBatchResult(YearMonth.of(2026, 8), 2, 0, 0, false));
        SettlementEstimateTask task = new SettlementEstimateTask(service);

        task.execute(new TaskExecutionContext(mock(TaskLease.class), progress));

        InOrder order = inOrder(progress, service);
        order.verify(progress).start("ESTIMATE", null);
        order.verify(service).calculateOpenActivityMonth(progress);
    }

    @Test
    void finalizationShowsItsStepBeforeDelegatingProgressToTheBatch() {
        SettlementBatchService service = mock(SettlementBatchService.class);
        TaskProgressReporter progress = mock(TaskProgressReporter.class);
        when(service.finalizeOpenActivityMonth(progress))
                .thenReturn(new SettlementBatchResult(YearMonth.of(2026, 7), 0, 0, 0, false));
        SettlementFinalizationTask task = new SettlementFinalizationTask(service);

        task.execute(new TaskExecutionContext(mock(TaskLease.class), progress));

        InOrder order = inOrder(progress, service);
        order.verify(progress).start("FINALIZE", null);
        order.verify(service).finalizeOpenActivityMonth(progress);
    }

    @Test
    void recalculationReportsAggregateResultsAfterTheExistingServiceCompletes() {
        SettlementRecalculationService service = mock(SettlementRecalculationService.class);
        TaskProgressReporter progress = mock(TaskProgressReporter.class);
        YearMonth month = YearMonth.of(2026, 7);
        when(service.recalculate(month, null, true))
                .thenReturn(new SettlementRecalculationResponse(
                        null, month, month.minusMonths(2), month, 2, 3,
                        1, 2, 1, 1, 1));
        SettlementRecalculationTask task =
                new SettlementRecalculationTask(service, month, null, true);

        task.execute(new TaskExecutionContext(mock(TaskLease.class), progress));

        InOrder order = inOrder(progress, service);
        order.verify(progress).start("RECALCULATE", null);
        order.verify(service).recalculate(month, null, true);
        order.verify(progress).start("RECALCULATE", 6);
        order.verify(progress).advance(4, 1, 1);
    }

    @Test
    void recalculationPreservesTheOriginalServiceFailure() {
        SettlementRecalculationService service = mock(SettlementRecalculationService.class);
        TaskProgressReporter progress = mock(TaskProgressReporter.class);
        RuntimeException failure = new RuntimeException("recalculation failed");
        when(service.recalculate(null, 10L, false)).thenThrow(failure);
        SettlementRecalculationTask task =
                new SettlementRecalculationTask(service, null, 10L, false);

        assertThatThrownBy(() -> task.execute(
                new TaskExecutionContext(mock(TaskLease.class), progress)))
                .isSameAs(failure);

        verify(progress).start("RECALCULATE", null);
        verifyNoMoreInteractions(progress);
    }
}
