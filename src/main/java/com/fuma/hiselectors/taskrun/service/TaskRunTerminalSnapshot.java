package com.fuma.hiselectors.taskrun.service;

import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskRunStatus;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TaskRunTerminalSnapshot(
        UUID runId,
        TaskType taskType,
        TriggerType triggerType,
        TaskRunStatus status,
        Instant startedAt,
        Instant finishedAt,
        Long totalCount,
        long processedCount,
        long succeededCount,
        long failedCount,
        long skippedCount,
        String errorType,
        String errorMessage) {

    public TaskRunTerminalSnapshot {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(taskType, "taskType");
        Objects.requireNonNull(triggerType, "triggerType");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(finishedAt, "finishedAt");
        if (!isTerminal(status)) {
            throw new IllegalArgumentException("status must be terminal");
        }
    }

    public static TaskRunTerminalSnapshot from(TaskRun run) {
        return new TaskRunTerminalSnapshot(
                run.getRunId(),
                run.getTaskType(),
                run.getTriggerType(),
                run.getStatus(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getTotalCount(),
                run.getProcessedCount(),
                run.getSucceededCount(),
                run.getFailedCount(),
                run.getSkippedCount(),
                run.getErrorType(),
                run.getErrorMessage());
    }

    private static boolean isTerminal(TaskRunStatus status) {
        return status == TaskRunStatus.SUCCEEDED
                || status == TaskRunStatus.PARTIAL_FAILED
                || status == TaskRunStatus.FAILED
                || status == TaskRunStatus.STALE;
    }
}
