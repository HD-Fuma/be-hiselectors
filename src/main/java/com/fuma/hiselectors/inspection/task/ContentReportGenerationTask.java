package com.fuma.hiselectors.inspection.task;

import com.fuma.hiselectors.inspection.service.ContentInspectionExecutionService;
import com.fuma.hiselectors.inspection.service.StaleContentInspectionService;
import com.fuma.hiselectors.taskrun.service.TaskExecutionContext;
import com.fuma.hiselectors.taskrun.service.TrackedTask;
import java.util.Set;
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
        staleContentInspectionService.reinspectStale(
                null,
                Set.of(),
                versionId -> contentInspectionExecutionService.inspectTracked(
                        versionId, context.lease()),
                snapshot -> {
                    if (snapshot.successCount() + snapshot.failureCount() == 0) {
                        context.progress().start(STEP, snapshot.targetCount());
                    }
                });
    }
}
