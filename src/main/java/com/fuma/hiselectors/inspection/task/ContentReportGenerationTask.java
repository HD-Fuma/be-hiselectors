package com.fuma.hiselectors.inspection.task;

import com.fuma.hiselectors.content.service.ContentBatchMode;
import com.fuma.hiselectors.inspection.dto.ReinspectStaleResponse;
import com.fuma.hiselectors.inspection.service.ContentInspectionExecutionService;
import com.fuma.hiselectors.inspection.service.StaleContentInspectionService;
import com.fuma.hiselectors.taskrun.service.TaskExecutionContext;
import com.fuma.hiselectors.taskrun.service.TrackedTask;
import java.util.Set;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContentReportGenerationTask implements TrackedTask {

    private static final String STEP = "STALE_CONTENT_INSPECTION";

    private final StaleContentInspectionService staleContentInspectionService;
    private final ContentInspectionExecutionService contentInspectionExecutionService;

    @Override
    public void execute(TaskExecutionContext context) {
        execute(context, ContentBatchMode.STANDARD);
    }

    public TrackedTask fastModeTask() {
        return context -> execute(context, ContentBatchMode.FAST);
    }

    private void execute(TaskExecutionContext context, ContentBatchMode mode) {
        Consumer<Long> inspector = versionId ->
                contentInspectionExecutionService.inspectTracked(versionId, context.lease());
        Consumer<ReinspectStaleResponse> progress = snapshot -> {
            if (snapshot.successCount() + snapshot.failureCount() == 0) {
                context.progress().start(STEP, snapshot.targetCount());
            }
        };
        if (mode == ContentBatchMode.STANDARD) {
            staleContentInspectionService.reinspectStale(
                    null, Set.of(), inspector, progress);
        } else {
            staleContentInspectionService.reinspectStale(
                    null, mode.targetAccountIds(), Set.of(), inspector, progress);
        }
    }
}
