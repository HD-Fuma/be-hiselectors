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
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
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
        return manualTask(ContentBatchMode.FAST);
    }

    public TrackedTask manualTask(ContentBatchMode mode) {
        AtomicReference<Set<Long>> changedVersionIds = new AtomicReference<>(Set.of());
        return new TrackedTask() {
            @Override
            public void execute(TaskExecutionContext context) {
                ContentBatchResult result = ContentSyncTask.this.executeBatch(context, mode);
                changedVersionIds.set(result.changedVersionIds());
            }

            @Override
            public void afterTerminal(TaskTerminalContext context) {
                ContentSyncTask.this.afterManualTerminal(
                        context, mode, changedVersionIds.get());
            }
        };
    }

    private void execute(TaskExecutionContext context, ContentBatchMode mode) {
        executeBatch(context, mode);
    }

    private ContentBatchResult executeBatch(
            TaskExecutionContext context, ContentBatchMode mode) {
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
        return result;
    }

    @Override
    public void afterTerminal(TaskTerminalContext context) {
        afterTerminal(context, ContentBatchMode.STANDARD);
    }

    private void afterTerminal(TaskTerminalContext context, ContentBatchMode mode) {
        switch (context.status()) {
            case SUCCEEDED, PARTIAL_FAILED, FAILED -> submitReport(
                    context.runId(), contentReportGenerationTask, mode, Set.of(), "ALL_STALE");
            case QUEUED, RUNNING, STALE -> {
            }
        }
    }

    private void afterManualTerminal(
            TaskTerminalContext context,
            ContentBatchMode mode,
            Set<Long> changedVersionIds) {
        if (context.status() != com.fuma.hiselectors.taskrun.model.TaskRunStatus.SUCCEEDED
                && context.status()
                != com.fuma.hiselectors.taskrun.model.TaskRunStatus.PARTIAL_FAILED) {
            return;
        }
        if (changedVersionIds.isEmpty()) {
            log.info("수동 콘텐츠 동기화에 신규·변경 버전이 없어 리포트 생성을 건너뜁니다. "
                    + "sourceContentSyncRunId={}", context.runId());
            return;
        }
        submitReport(
                context.runId(),
                contentReportGenerationTask.versionIdsTask(changedVersionIds),
                mode,
                changedVersionIds,
                "CHANGED_VERSIONS");
    }

    private void submitReport(
            UUID sourceRunId,
            TrackedTask reportTask,
            ContentBatchMode mode,
            Set<Long> contentVersionIds,
            String inspectionScope) {
        var payload = objectMapper.createObjectNode()
                .put("sourceContentSyncRunId", sourceRunId.toString())
                .put("inspectionScope", inspectionScope);
        if (mode == ContentBatchMode.FAST) {
            payload.put("fastMode", true);
        }
        if (!contentVersionIds.isEmpty()) {
            var ids = payload.putArray("contentVersionIds");
            contentVersionIds.stream().sorted().forEach(ids::add);
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
