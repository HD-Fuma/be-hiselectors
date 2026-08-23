package com.fuma.hiselectors.settlement.task;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.settlement.service.SettlementBatchService;
import com.fuma.hiselectors.settlement.service.SettlementBatchService.SettlementBatchResult;
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
}
