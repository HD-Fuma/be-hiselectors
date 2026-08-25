package com.fuma.hiselectors.inspection.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.fuma.hiselectors.inspection.dto.ReinspectStaleResponse;
import com.fuma.hiselectors.inspection.service.ContentInspectionExecutionService;
import com.fuma.hiselectors.inspection.service.StaleContentInspectionService;
import com.fuma.hiselectors.taskrun.service.TaskExecutionContext;
import com.fuma.hiselectors.taskrun.service.TaskLease;
import com.fuma.hiselectors.taskrun.service.TaskProgressReporter;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class ContentReportGenerationTaskTest {

    @Test
    void executesTrackedStaleInspectionAndStartsProgressFromInitialSnapshot() throws Exception {
        StaleContentInspectionService staleContentInspectionService =
                mock(StaleContentInspectionService.class);
        ContentInspectionExecutionService contentInspectionExecutionService =
                mock(ContentInspectionExecutionService.class);
        ContentReportGenerationTask task = new ContentReportGenerationTask(
                staleContentInspectionService,
                contentInspectionExecutionService);
        TaskLease lease = mock(TaskLease.class);
        TaskProgressReporter progress = mock(TaskProgressReporter.class);
        ReinspectStaleResponse initial = new ReinspectStaleResponse(3, 0, 0, List.of());
        ReinspectStaleResponse completed = new ReinspectStaleResponse(3, 1, 0, List.of());
        doAnswer(invocation -> {
            Consumer<Long> inspector = invocation.getArgument(2);
            Consumer<ReinspectStaleResponse> callback = invocation.getArgument(3);
            callback.accept(initial);
            inspector.accept(11L);
            callback.accept(completed);
            return completed;
        }).when(staleContentInspectionService)
                .reinspectStale(eq(null), eq(Set.of()), any(), any());

        task.execute(new TaskExecutionContext(lease, progress));

        verify(staleContentInspectionService)
                .reinspectStale(eq(null), eq(Set.of()), any(), any());
        verifyNoMoreInteractions(staleContentInspectionService);
        verify(contentInspectionExecutionService).inspectTracked(11L, lease);
        verify(progress).start("STALE_CONTENT_INSPECTION", 3);
        verify(progress, never()).advance(anyInt(), anyInt(), anyInt());
    }
}
