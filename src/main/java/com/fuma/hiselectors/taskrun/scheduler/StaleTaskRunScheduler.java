package com.fuma.hiselectors.taskrun.scheduler;

import com.fuma.hiselectors.taskrun.config.TaskTypePolicy;
import com.fuma.hiselectors.taskrun.logging.TaskRunFailureLogger;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskRunStatus;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.repository.TaskRunRepository;
import com.fuma.hiselectors.taskrun.service.TaskRunProgressStream;
import com.fuma.hiselectors.taskrun.service.TaskRunTerminalSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class StaleTaskRunScheduler {

    private static final Logger log = LoggerFactory.getLogger(StaleTaskRunScheduler.class);

    private static final EnumSet<TaskRunStatus> ACTIVE_STATUSES =
            EnumSet.of(TaskRunStatus.QUEUED, TaskRunStatus.RUNNING);

    private final TaskRunRepository repository;
    private final TaskTypePolicy policy;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final TaskRunFailureLogger failureLogger;
    private final TaskRunProgressStream progressStream;

    public StaleTaskRunScheduler(
            TaskRunRepository repository,
            TaskTypePolicy policy,
            TransactionTemplate transactions,
            Clock clock,
            TaskRunFailureLogger failureLogger,
            TaskRunProgressStream progressStream) {
        this.repository = repository;
        this.policy = policy;
        this.transactions = transactions;
        this.clock = clock;
        this.failureLogger = failureLogger;
        this.progressStream = progressStream;
    }

    @Scheduled(fixedDelayString = "${task-run.stale.fixed-delay:60000}")
    public void markStaleRuns() {
        Instant now = clock.instant();
        for (TaskType type : TaskType.values()) {
            Instant cutoff = now.minus(policy.forType(type).staleTimeout());
            for (UUID runId : repository.findStaleCandidateRunIds(type, ACTIVE_STATUSES, cutoff)) {
                TaskRunTerminalSnapshot snapshot =
                        transactions.execute(ignored -> markIfStillStale(runId, now));
                if (snapshot != null) {
                    progressStream.publishChanged(snapshot.runId());
                    logFailure(snapshot);
                }
            }
        }
    }

    private TaskRunTerminalSnapshot markIfStillStale(UUID runId, Instant now) {
        TaskRun run = repository.findByRunIdForUpdate(runId).orElse(null);
        if (run == null || run.isQueueManaged() || !ACTIVE_STATUSES.contains(run.getStatus())) {
            return null;
        }
        TaskTypePolicy.Settings settings = policy.forType(run.getTaskType());
        Instant cutoff = now.minus(settings.staleTimeout());
        if (!run.getHeartbeatAt().isBefore(cutoff)) {
            return null;
        }
        run.markStale(UUID.randomUUID(), settings.singleton(), now);
        repository.flush();
        return TaskRunTerminalSnapshot.from(run);
    }

    private void logFailure(TaskRunTerminalSnapshot snapshot) {
        try {
            failureLogger.log(snapshot);
        } catch (Exception exception) {
            log.error("Task run {} stale event logging failed", snapshot.runId(), exception);
        }
    }
}
