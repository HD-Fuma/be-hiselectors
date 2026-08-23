package com.fuma.hiselectors.taskrun.service;

import com.fuma.hiselectors.taskrun.config.TaskRunProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** One reporter belongs to one worker thread; instances are intentionally not thread-safe. */
public final class ThrottledTaskProgressReporter implements TaskProgressReporter {

    private final TaskLease lease;
    private final TaskLeaseTransaction transaction;
    private final int flushCount;
    private final long flushIntervalMs;
    private final Clock clock;

    private long pendingSucceeded;
    private long pendingFailed;
    private long pendingSkipped;
    private Instant lastFlushAt;

    public ThrottledTaskProgressReporter(
            TaskLease lease,
            TaskLeaseTransaction transaction,
            TaskRunProperties.Progress properties,
            Clock clock) {
        this.lease = Objects.requireNonNull(lease, "lease must not be null");
        this.transaction = Objects.requireNonNull(transaction, "transaction must not be null");
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
        if (intervalElapsed(clock.instant())) {
            persist(null, null, false);
        }
    }

    void flush() {
        if (pendingItems() > 0) {
            persist(null, null, false);
        }
    }

    private void persist(String stepCode, Integer totalCount, boolean updateTotal) {
        Instant now = clock.instant();
        transaction.apply(
                lease,
                stepCode,
                totalCount,
                updateTotal,
                pendingSucceeded,
                pendingFailed,
                pendingSkipped,
                now);
        pendingSucceeded = 0;
        pendingFailed = 0;
        pendingSkipped = 0;
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
}
