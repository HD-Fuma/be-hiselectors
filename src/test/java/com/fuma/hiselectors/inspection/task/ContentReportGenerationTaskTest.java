package com.fuma.hiselectors.inspection.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.inspection.dto.ReinspectStaleResponse;
import com.fuma.hiselectors.inspection.service.ContentInspectionExecutionService;
import com.fuma.hiselectors.inspection.service.StaleContentInspectionService;
import com.fuma.hiselectors.taskrun.service.TaskExecutionContext;
import com.fuma.hiselectors.taskrun.service.TaskLease;
import com.fuma.hiselectors.taskrun.service.TaskProgressReporter;
import com.fuma.hiselectors.taskrun.service.TrackedTask;
import java.util.List;
import java.util.Map;
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

    @Test
    void fastModeScopesStaleInspectionToConfiguredAccounts() throws Exception {
        StaleContentInspectionService staleContentInspectionService =
                mock(StaleContentInspectionService.class);
        ContentInspectionExecutionService contentInspectionExecutionService =
                mock(ContentInspectionExecutionService.class);
        ContentReportGenerationTask task = new ContentReportGenerationTask(
                staleContentInspectionService,
                contentInspectionExecutionService);
        TaskLease lease = mock(TaskLease.class);
        TaskProgressReporter progress = mock(TaskProgressReporter.class);
        TrackedTask fastModeTask = task.fastModeTask();

        fastModeTask.execute(new TaskExecutionContext(lease, progress));

        verify(staleContentInspectionService).reinspectStale(
                eq(null),
                eq(Map.of(
                        SnsPlatform.YOUTUBE, "UCD2RQE52TloxzZxZ2fyq8HQ",
                        SnsPlatform.INSTAGRAM, "hi_selectors")),
                eq(Set.of()),
                any(),
                any());
    }

    @Test
    void versionIdsTaskInspectsOnlyExplicitlySelectedVersions() throws Exception {
        StaleContentInspectionService staleContentInspectionService =
                mock(StaleContentInspectionService.class);
        ContentInspectionExecutionService contentInspectionExecutionService =
                mock(ContentInspectionExecutionService.class);
        ContentReportGenerationTask task = new ContentReportGenerationTask(
                staleContentInspectionService,
                contentInspectionExecutionService);
        TaskLease lease = mock(TaskLease.class);
        TaskProgressReporter progress = mock(TaskProgressReporter.class);
        doAnswer(invocation -> {
            Consumer<Long> inspector = invocation.getArgument(1);
            Consumer<ReinspectStaleResponse> callback = invocation.getArgument(2);
            callback.accept(new ReinspectStaleResponse(2, 0, 0, List.of()));
            inspector.accept(11L);
            inspector.accept(22L);
            return new ReinspectStaleResponse(2, 2, 0, List.of());
        }).when(staleContentInspectionService)
                .reinspectVersionIds(eq(Set.of(11L, 22L)), any(), any());

        task.versionIdsTask(Set.of(11L, 22L))
                .execute(new TaskExecutionContext(lease, progress));

        verify(staleContentInspectionService)
                .reinspectVersionIds(eq(Set.of(11L, 22L)), any(), any());
        verify(contentInspectionExecutionService).inspectTracked(11L, lease);
        verify(contentInspectionExecutionService).inspectTracked(22L, lease);
        verify(progress).start("STALE_CONTENT_INSPECTION", 2);
    }
}
