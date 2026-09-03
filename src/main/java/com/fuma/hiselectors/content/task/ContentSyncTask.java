package com.fuma.hiselectors.content.task;

import com.fuma.hiselectors.content.service.ContentBatchService;
import com.fuma.hiselectors.content.service.ContentBatchService.ContentBatchResult;
import com.fuma.hiselectors.content.service.ContentBatchMode;
import com.fuma.hiselectors.inspection.task.ContentReportGenerationTask;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import com.fuma.hiselectors.taskrun.service.TaskExecutionContext;
import com.fuma.hiselectors.taskrun.service.TaskRunExecutionService;
import com.fuma.hiselectors.taskrun.service.TaskStartCommand;
import com.fuma.hiselectors.taskrun.service.TaskStartResult;
import com.fuma.hiselectors.taskrun.service.TaskTerminalContext;
import com.fuma.hiselectors.taskrun.service.TrackedTask;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContentSyncTask implements TrackedTask {

    private final ContentBatchService contentBatchService;
    private final TaskRunExecutionService taskRunExecutionService;
    private final ContentReportGenerationTask contentReportGenerationTask;
    private final ObjectMapper objectMapper;

    @Override
    public void execute(TaskExecutionContext context) {
        execute(context, ContentBatchMode.STANDARD);
    }

    public TrackedTask fastModeTask() {
        return new TrackedTask() {
            @Override
            public void execute(TaskExecutionContext context) {
                ContentSyncTask.this.execute(context, ContentBatchMode.FAST);
            }

            @Override
            public void afterTerminal(TaskTerminalContext context) {
                ContentSyncTask.this.afterTerminal(context, ContentBatchMode.FAST);
            }
        };
    }

    private void execute(TaskExecutionContext context, ContentBatchMode mode) {
        ContentBatchResult result = mode == ContentBatchMode.STANDARD
                ? contentBatchService.run(context.progress())
                : contentBatchService.run(context.progress(), mode);
        log.info(
                "콘텐츠 배치 완료: newContentCount={}, engagementCount={}, "
                        + "newContentSucceeded={}, storedContentSucceeded={}",
                result.newContentCount(),
                result.engagementCount(),
                result.newContentSucceeded(),
                result.storedContentSucceeded());
    }

    @Override
    public void afterTerminal(TaskTerminalContext context) {
        afterTerminal(context, ContentBatchMode.STANDARD);
    }

    private void afterTerminal(TaskTerminalContext context, ContentBatchMode mode) {
        switch (context.status()) {
            case SUCCEEDED, PARTIAL_FAILED, FAILED -> submitReport(context.runId(), mode);
            case QUEUED, RUNNING, STALE -> {
            }
        }
    }

    private void submitReport(UUID sourceRunId, ContentBatchMode mode) {
        var payload = objectMapper.createObjectNode()
                .put("sourceContentSyncRunId", sourceRunId.toString());
        TrackedTask reportTask = contentReportGenerationTask;
        if (mode == ContentBatchMode.FAST) {
            payload.put("fastMode", true);
            reportTask = contentReportGenerationTask.fastModeTask();
        }
        TaskStartResult result = taskRunExecutionService.submit(
                new TaskStartCommand(
                        TaskType.CONTENT_REPORT_GENERATION,
                        TriggerType.SCHEDULED,
                        null,
                        UUID.nameUUIDFromBytes(
                                ("content-report-after-content-sync:" + sourceRunId)
                                        .getBytes(StandardCharsets.UTF_8)),
                        payload),
                reportTask);
        if (result instanceof TaskStartResult.ActiveConflict conflict) {
            log.info(
                    "콘텐츠 리포트 실행 충돌: sourceContentSyncRunId={}, activeReportRunId={}",
                    sourceRunId,
                    conflict.activeRun().getRunId());
        }
    }

}
