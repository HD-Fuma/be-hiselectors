package com.fuma.hiselectors.taskrun.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskRunStatus;
import com.fuma.hiselectors.taskrun.repository.TaskRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskLeaseTransaction {

    private final TaskRunRepository repository;
    private final Clock clock;

    public TaskLeaseTransaction(TaskRunRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void apply(
            TaskLease lease,
            String stepCode,
            Integer totalCount,
            boolean updateTotal,
            long succeededDelta,
            long failedDelta,
            long skippedDelta,
            Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        TaskRun run = lockRunningLease(lease);
        if (updateTotal && totalCount != null) {
            run.setTotal(totalCount, now);
        }
        if (stepCode != null) {
            run.changeStep(stepCode, now);
        }
        run.addCounts(succeededDelta, failedDelta, skippedDelta, now);
        repository.flush();
    }

    /** The callback must contain database work only; perform external I/O before entering this transaction. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(
            TaskLease lease,
            long succeededDelta,
            long failedDelta,
            long skippedDelta,
            LeaseProtectedWork work) {
        TaskRun run = lockRunningLease(lease);
        Objects.requireNonNull(work, "work must not be null").run();
        run.addCounts(succeededDelta, failedDelta, skippedDelta, clock.instant());
        repository.flush();
    }

    private TaskRun lockRunningLease(TaskLease lease) {
        Objects.requireNonNull(lease, "lease must not be null");
        TaskRun run = repository.findByRunIdForUpdate(lease.runId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_RUN_NOT_FOUND));
        if (run.getStatus() != TaskRunStatus.RUNNING
                || !Objects.equals(run.getLeaseToken(), lease.token())) {
            throw new BusinessException(ErrorCode.TASK_RUN_LEASE_LOST);
        }
        return run;
    }

    @FunctionalInterface
    public interface LeaseProtectedWork {

        void run();
    }
}
