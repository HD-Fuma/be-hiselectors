package com.fuma.hiselectors.taskrun.service;

import com.fuma.hiselectors.taskrun.config.TaskRunProperties;
import com.fuma.hiselectors.taskrun.model.TaskStepProgress;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/** One reporter belongs to one worker thread; instances are intentionally not thread-safe. */
@Slf4j
public final class ThrottledTaskProgressReporter implements TaskProgressReporter {

    private static final Set<String> STREAMED_STEP_KEYS =
            Set.of("NEW_CONTENT_SYNC", "STORED_CONTENT_SYNC");

    private final TaskLease lease;
    private final TaskLeaseTransaction transaction;
    private final TaskRunProgressStream progressStream;
    private final int flushCount;
    private final long flushIntervalMs;
    private final Clock clock;

    private long pendingSucceeded;
    private long pendingFailed;
    private long pendingSkipped;
    private String pendingMessage;
    private final LinkedHashMap<String, TaskStepProgress> pendingStepProgress =
            new LinkedHashMap<>();
    private Instant lastFlushAt;

    public ThrottledTaskProgressReporter(
            TaskLease lease,
            TaskLeaseTransaction transaction,
            TaskRunProgressStream progressStream,
            TaskRunProperties.Progress properties,
            Clock clock) {
        this.lease = Objects.requireNonNull(lease, "lease must not be null");
        this.transaction = Objects.requireNonNull(transaction, "transaction must not be null");
        this.progressStream = Objects.requireNonNull(
                progressStream, "progressStream must not be null");
        Objects.requireNonNull(properties, "properties must not be null");
        this.flushCount = properties.flushCount();
        this.flushIntervalMs = properties.flushIntervalMs();
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.lastFlushAt = clock.instant();
    }

    @Override
    public void start(String stepCode, Integer totalCount) {
        if (totalCount != null && totalCount < 0) {
            throw new IllegalArgumentException("totalCount must not be negative");
        }
        persist(stepCode, totalCount, true);
    }

    @Override
    public void changeStep(String stepCode) {
        persist(stepCode, null, false);
    }

    @Override
    public void describe(String message) {
        Objects.requireNonNull(message, "진행 메시지는 필수입니다.");
        if (message.length() > 500) {
            throw new IllegalArgumentException("진행 메시지는 500자를 초과할 수 없습니다.");
        }
        pendingMessage = message;
    }

    @Override
    public void reportStep(String stepKey, Long totalCount, long processedCount) {
        requireValidStepKey(stepKey);
        pendingStepProgress.put(stepKey, new TaskStepProgress(totalCount, processedCount));
        if (STREAMED_STEP_KEYS.contains(stepKey)) {
            publishStepProgress(stepKey, totalCount, processedCount);
        }
    }

    @Override
    public void advance(int succeededDelta, int failedDelta, int skippedDelta) {
        requireNonNegative(succeededDelta, failedDelta, skippedDelta);
        pendingSucceeded = Math.addExact(pendingSucceeded, succeededDelta);
        pendingFailed = Math.addExact(pendingFailed, failedDelta);
        pendingSkipped = Math.addExact(pendingSkipped, skippedDelta);
        if (pendingItems() >= flushCount || intervalElapsed(clock.instant())) {
            persist(null, null, false);
        }
    }

    @Override
    public void heartbeat() {
        if (!pendingStepProgress.isEmpty() || intervalElapsed(clock.instant())) {
            persist(null, null, false);
        }
    }

    void flush() {
        if (pendingItems() > 0 || pendingMessage != null || !pendingStepProgress.isEmpty()) {
            persist(null, null, false);
        }
    }

    private void persist(String stepCode, Integer totalCount, boolean updateTotal) {
        Instant now = clock.instant();
        Map<String, TaskStepProgress> stepProgressPatch = pendingStepProgress.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(pendingStepProgress));
        transaction.apply(
                lease,
                stepCode,
                totalCount,
                updateTotal,
                pendingMessage,
                pendingSucceeded,
                pendingFailed,
                pendingSkipped,
                stepProgressPatch,
                now);
        pendingSucceeded = 0;
        pendingFailed = 0;
        pendingSkipped = 0;
        pendingMessage = null;
        pendingStepProgress.clear();
        lastFlushAt = now;
    }

    private long pendingItems() {
        return Math.addExact(Math.addExact(pendingSucceeded, pendingFailed), pendingSkipped);
    }

    private boolean intervalElapsed(Instant now) {
        return !now.isBefore(lastFlushAt.plusMillis(flushIntervalMs));
    }

    private void requireNonNegative(int succeeded, int failed, int skipped) {
        if (succeeded < 0 || failed < 0 || skipped < 0) {
            throw new IllegalArgumentException("progress deltas must not be negative");
        }
    }

    private void requireValidStepKey(String stepKey) {
        if (stepKey == null || stepKey.isBlank()) {
            throw new IllegalArgumentException("단계 키는 비어 있을 수 없습니다.");
        }
        if (stepKey.length() > 100) {
            throw new IllegalArgumentException("단계 키는 100자를 초과할 수 없습니다.");
        }
    }

    private void publishStepProgress(String stepKey, Long totalCount, long processedCount) {
        try {
            progressStream.publish(new TaskRunProgressEvent(
                    lease.runId(), stepKey, totalCount, processedCount));
        } catch (RuntimeException failure) {
            log.warn("TaskRun progress stream publish failed: runId={}, stepKey={}",
                    lease.runId(), stepKey, failure);
        }
    }
}
