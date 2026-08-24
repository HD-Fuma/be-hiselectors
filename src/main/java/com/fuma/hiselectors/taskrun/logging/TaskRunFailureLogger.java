package com.fuma.hiselectors.taskrun.logging;

import com.fuma.hiselectors.logging.BatchEventLogger;
import com.fuma.hiselectors.taskrun.model.TaskRunStatus;
import com.fuma.hiselectors.taskrun.service.TaskRunTerminalSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class TaskRunFailureLogger {

    private final BatchEventLogger batchEventLogger;

    public TaskRunFailureLogger(BatchEventLogger batchEventLogger) {
        this.batchEventLogger = batchEventLogger;
    }

    public void log(TaskRunTerminalSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Fallback fallback = fallback(snapshot.status());

        Map<String, Long> counts = new LinkedHashMap<>();
        if (snapshot.totalCount() != null) {
            counts.put("total", snapshot.totalCount());
        }
        counts.put("processed", snapshot.processedCount());
        counts.put("succeeded", snapshot.succeededCount());
        counts.put("failed", snapshot.failedCount());
        counts.put("skipped", snapshot.skippedCount());

        batchEventLogger.taskRunTerminal(
                snapshot.runId(),
                snapshot.status().name(),
                snapshot.startedAt(),
                snapshot.finishedAt(),
                counts,
                Map.of(
                        "taskType", snapshot.taskType().name(),
                        "triggerType", snapshot.triggerType().name()),
                nonblankOr(snapshot.errorType(), fallback.type()),
                nonblankOr(snapshot.errorMessage(), fallback.message()));
    }

    private static Fallback fallback(TaskRunStatus status) {
        return switch (status) {
            case FAILED -> new Fallback(
                    "TASK_RUN_FAILED", "처리 결과에 실패 건수가 포함되어 있습니다.");
            case PARTIAL_FAILED -> new Fallback(
                    "TASK_RUN_PARTIAL_FAILED", "일부 처리 항목이 실패했습니다.");
            case STALE -> new Fallback(
                    "TASK_RUN_STALE", "제한 시간 동안 heartbeat가 없어 비정상 종료로 판정했습니다.");
            default -> throw new IllegalArgumentException("Only failed task runs can be logged");
        };
    }

    private static String nonblankOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record Fallback(String type, String message) {
    }
}
