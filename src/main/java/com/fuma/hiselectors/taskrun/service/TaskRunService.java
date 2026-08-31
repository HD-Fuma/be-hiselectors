package com.fuma.hiselectors.taskrun.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.taskrun.config.TaskTypePolicy;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskRunStatus;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.repository.TaskRunRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskRunService {

    private final TaskRunRepository repository;
    private final TaskRunCreator creator;
    private final TaskRunConflictResolver conflictResolver;
    private final RequestFingerprint requestFingerprint;
    private final TaskTypePolicy taskTypePolicy;
    private final Clock clock;

    public TaskRunService(
            TaskRunRepository repository,
            TaskRunCreator creator,
            TaskRunConflictResolver conflictResolver,
            RequestFingerprint requestFingerprint,
            TaskTypePolicy taskTypePolicy,
            Clock clock) {
        this.repository = repository;
        this.creator = creator;
        this.conflictResolver = conflictResolver;
        this.requestFingerprint = requestFingerprint;
        this.taskTypePolicy = taskTypePolicy;
        this.clock = clock;
    }

    public TaskStartResult start(TaskStartCommand command) {
        return start(command, false);
    }

    public TaskStartResult start(TaskStartCommand command, boolean queueManaged) {
        Objects.requireNonNull(command, "command must not be null");
        String fingerprint = requestFingerprint.of(command.taskType(), command.businessPayload());
        String concurrencyKey = concurrencyKey(command.taskType());
        try {
            return new TaskStartResult.Created(queueManaged
                    ? creator.create(command, fingerprint, concurrencyKey, true)
                    : creator.create(command, fingerprint, concurrencyKey));
        } catch (DataIntegrityViolationException conflict) {
            return conflictResolver.resolve(command, fingerprint, concurrencyKey, conflict);
        }
    }

    @Transactional
    public UUID markRunning(UUID runId) {
        TaskRun run = find(runId);
        UUID leaseToken = UUID.randomUUID();
        transition(() -> run.markRunning(leaseToken, clock.instant()));
        return leaseToken;
    }

    @Transactional
    public TaskRunTerminalSnapshot complete(UUID runId, UUID leaseToken) {
        TaskRun run = find(runId);
        requireLease(run, leaseToken);
        transition(() -> run.complete(clock.instant()));
        return TaskRunTerminalSnapshot.from(run);
    }

    @Transactional
    public TaskRunTerminalSnapshot fail(
            UUID runId, UUID leaseToken, String errorType, String errorMessage) {
        TaskRun run = find(runId);
        requireStatus(run, TaskRunStatus.RUNNING);
        requireLease(run, leaseToken);
        transition(() -> run.fail(
                bounded(errorType, 100),
                bounded(errorMessage, 1000),
                clock.instant()));
        return TaskRunTerminalSnapshot.from(run);
    }

    @Transactional
    public TaskRunTerminalSnapshot failQueued(UUID runId, String errorType, String errorMessage) {
        TaskRun run = find(runId);
        requireStatus(run, TaskRunStatus.QUEUED);
        transition(() -> run.fail(
                bounded(errorType, 100),
                bounded(errorMessage, 1000),
                clock.instant()));
        return TaskRunTerminalSnapshot.from(run);
    }

    @Transactional
    public void markStale(UUID runId, UUID replacementToken, boolean clearConcurrencyKey) {
        TaskRun run = find(runId);
        transition(() -> run.markStale(replacementToken, clearConcurrencyKey, clock.instant()));
    }

    private TaskRun find(UUID runId) {
        return repository.findByRunId(Objects.requireNonNull(runId, "runId must not be null"))
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_RUN_NOT_FOUND));
    }

    private void requireLease(TaskRun run, UUID leaseToken) {
        if (leaseToken == null || !Objects.equals(run.getLeaseToken(), leaseToken)) {
            throw new BusinessException(ErrorCode.TASK_RUN_LEASE_LOST);
        }
    }

    private void requireStatus(TaskRun run, TaskRunStatus expected) {
        if (run.getStatus() != expected) {
            throw new BusinessException(ErrorCode.INVALID_TASK_RUN_TRANSITION);
        }
    }

    private void transition(Runnable transition) {
        try {
            transition.run();
            repository.flush();
        } catch (IllegalStateException | OptimisticLockingFailureException exception) {
            throw new BusinessException(ErrorCode.INVALID_TASK_RUN_TRANSITION);
        }
    }

    private String concurrencyKey(TaskType taskType) {
        return taskTypePolicy.forType(taskType).singleton() ? taskType.name() : null;
    }

    private String bounded(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
