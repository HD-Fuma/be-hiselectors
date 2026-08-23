package com.fuma.hiselectors.taskrun.scheduler;

import com.fuma.hiselectors.taskrun.config.TaskTypePolicy;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskRunStatus;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.repository.TaskRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class StaleTaskRunScheduler {

    private static final EnumSet<TaskRunStatus> ACTIVE_STATUSES =
            EnumSet.of(TaskRunStatus.QUEUED, TaskRunStatus.RUNNING);

    private final TaskRunRepository repository;
    private final TaskTypePolicy policy;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public StaleTaskRunScheduler(
            TaskRunRepository repository,
            TaskTypePolicy policy,
            TransactionTemplate transactions,
            Clock clock) {
        this.repository = repository;
        this.policy = policy;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${task-run.stale.fixed-delay:60000}")
    public void markStaleRuns() {
        Instant now = clock.instant();
        for (TaskType type : TaskType.values()) {
            Instant cutoff = now.minus(policy.forType(type).staleTimeout());
            for (UUID runId : repository.findStaleCandidateRunIds(type, ACTIVE_STATUSES, cutoff)) {
                transactions.executeWithoutResult(ignored -> markIfStillStale(runId, now));
            }
        }
    }

    private void markIfStillStale(UUID runId, Instant now) {
        TaskRun run = repository.findByRunIdForUpdate(runId).orElse(null);
        if (run == null || !ACTIVE_STATUSES.contains(run.getStatus())) {
            return;
        }
        TaskTypePolicy.Settings settings = policy.forType(run.getTaskType());
        Instant cutoff = now.minus(settings.staleTimeout());
        if (!run.getHeartbeatAt().isBefore(cutoff)) {
            return;
        }
        run.markStale(UUID.randomUUID(), settings.fencedPersistence(), now);
        repository.flush();
    }
}
