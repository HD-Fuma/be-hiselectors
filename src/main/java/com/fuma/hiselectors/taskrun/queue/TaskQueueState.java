package com.fuma.hiselectors.taskrun.queue;

import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskRunStatus;
import com.fuma.hiselectors.taskrun.repository.TaskRunRepository;
import com.fuma.hiselectors.taskrun.service.TaskLease;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The database lease, not the SQS receive count, authorizes a business attempt. */
@Service
public class TaskQueueState {
    private final TaskRunRepository repository;
    private final Clock clock;
    private final TaskQueueProperties properties;

    public TaskQueueState(TaskRunRepository repository, Clock clock, TaskQueueProperties properties) {
        this.repository = repository;
        this.clock = clock;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public TaskRun get(UUID runId) {
        return repository.findByRunId(runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown queued run"));
    }

    @Transactional
    public Claim claim(UUID runId, boolean retrySafe) {
        TaskRun run = lock(runId);
        if (!run.isQueueManaged()) {
            throw new IllegalArgumentException("Queue cannot execute a legacy local run");
        }
        if (run.isTerminal()) {
            return new Claim(run, null, run.getStatus() == TaskRunStatus.SUCCEEDED
                    ? Disposition.SUCCEEDED : Disposition.FAILED);
        }
        Instant now = clock.instant();
        if ((run.getQueueLeaseUntil() != null && run.getQueueLeaseUntil().isAfter(now))
                || (run.getQueueAvailableAt() != null && run.getQueueAvailableAt().isAfter(now))) {
            return new Claim(run, null, Disposition.BUSY);
        }
        if (run.getQueueAttempts() >= properties.maxAttempts()
                || (run.getStatus() == TaskRunStatus.RUNNING
                    && (!retrySafe || run.getSucceededCount() > 0))) {
            run.fail("QUEUE_RECOVERY_REQUIRES_REVIEW",
                    "Worker interrupted; retry budget exhausted or business operation requires review", now);
            return new Claim(run, null, Disposition.FAILED);
        }
        TaskLease lease = new TaskLease(runId, UUID.randomUUID());
        run.startQueueAttempt(lease.token(), now, now.plusSeconds(properties.leaseSeconds()));
        repository.flush();
        return new Claim(run, lease, Disposition.CLAIMED);
    }

    @Transactional
    public void heartbeat(TaskLease lease) {
        TaskRun run = owned(lease);
        Instant now = clock.instant();
        run.renewQueueLease(now, now.plusSeconds(properties.leaseSeconds()));
    }

    @Transactional
    public Disposition finish(TaskLease lease, Exception failure, boolean retrySafe) {
        TaskRun run = owned(lease);
        Instant now = clock.instant();
        boolean unsuccessful = failure != null || run.getFailedCount() > 0;
        String errorType = failure == null ? "QUEUE_TASK_FAILED" : failure.getClass().getSimpleName();
        // Do not persist exception payloads: external clients can include credentials or content.
        String errorMessage = "Queue attempt failed; inspect the correlated task logs";
        if (unsuccessful && retrySafe && run.getSucceededCount() == 0
                && run.getQueueAttempts() < properties.maxAttempts()) {
            run.retryQueue(now, now.plusSeconds(properties.retryDelaySeconds()), errorType, errorMessage);
            return Disposition.RETRY;
        }
        if (failure == null) {
            run.complete(now);
        } else {
            run.fail(errorType, errorMessage, now);
        }
        return run.getStatus() == TaskRunStatus.SUCCEEDED ? Disposition.SUCCEEDED : Disposition.FAILED;
    }

    @Transactional
    public void reject(UUID runId) {
        TaskRun run = lock(runId);
        if (run.isQueueManaged() && (run.getStatus() == TaskRunStatus.QUEUED
                || (run.getStatus() == TaskRunStatus.RUNNING && run.getQueueLeaseUntil() != null
                    && !run.getQueueLeaseUntil().isAfter(clock.instant())))) {
            run.fail("INVALID_QUEUE_COMMAND", "Stored command cannot be executed safely", clock.instant());
        }
    }

    @Transactional
    public void markEnqueued(UUID runId) {
        TaskRun run = lock(runId);
        if (run.isQueueManaged()) {
            run.recordEnqueued(clock.instant());
        }
    }

    private TaskRun lock(UUID runId) {
        return repository.findByRunIdForUpdate(runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown queued run"));
    }

    private TaskRun owned(TaskLease lease) {
        TaskRun run = lock(lease.runId());
        if (run.getStatus() != TaskRunStatus.RUNNING
                || !Objects.equals(run.getLeaseToken(), lease.token())) {
            throw new IllegalStateException("Queue worker no longer owns this run");
        }
        return run;
    }

    public enum Disposition { CLAIMED, BUSY, RETRY, SUCCEEDED, FAILED }
    public record Claim(TaskRun run, TaskLease lease, Disposition disposition) { }
}
