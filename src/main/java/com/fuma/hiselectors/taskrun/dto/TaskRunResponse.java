package com.fuma.hiselectors.taskrun.dto;

import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskRunStatus;
import com.fuma.hiselectors.taskrun.model.TaskStepProgress;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record TaskRunResponse(
        UUID runId,
        TaskType taskType,
        TriggerType triggerType,
        TaskRunStatus status,
        String currentStep,
        String progressMessage,
        Map<String, TaskStepProgress> stepProgress,
        Long totalCount,
        long processedCount,
        long succeededCount,
        long failedCount,
        long skippedCount,
        Integer progressPercent,
        StartedBy startedBy,
        Instant startedAt,
        Instant finishedAt) {

    public TaskRunResponse {
        if (stepProgress != null) {
            stepProgress = Collections.unmodifiableMap(new LinkedHashMap<>(stepProgress));
        }
    }

    public static TaskRunResponse from(TaskRun run, Map<Long, String> adminNames) {
        Long adminId = run.getStartedByAdminId();
        return new TaskRunResponse(
                run.getRunId(),
                run.getTaskType(),
                run.getTriggerType(),
                run.getStatus(),
                run.getCurrentStep(),
                run.getProgressMessage(),
                run.getStepProgress(),
                run.getTotalCount(),
                run.getProcessedCount(),
                run.getSucceededCount(),
                run.getFailedCount(),
                run.getSkippedCount(),
                progressPercent(run),
                adminId == null ? null : new StartedBy(adminId, adminNames.get(adminId)),
                run.getStartedAt(),
                run.getFinishedAt());
    }

    private static Integer progressPercent(TaskRun run) {
        Long total = run.getTotalCount();
        if (total == null || total <= 0) {
            return null;
        }
        double percent = run.getProcessedCount() * 100.0 / total;
        return (int) Math.max(0, Math.min(100, percent));
    }

    public record StartedBy(Long adminId, String name) {
    }
}
