package com.fuma.hiselectors.taskrun.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.taskrun.config.TaskRunProperties;
import com.fuma.hiselectors.taskrun.logging.TaskRunFailureLogger;
import com.fuma.hiselectors.taskrun.model.TaskRunStatus;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class TaskRunExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TaskRunExecutionService.class);
    private static final String EXECUTOR_REJECTED = "EXECUTOR_REJECTED";

    private final TaskRunService taskRunService;
    private final TaskLeaseTransaction leaseTransaction;
    private final TaskRunProperties properties;
    private final Clock clock;
    private final TaskExecutor executor;
    private final TaskRunFailureLogger failureLogger;

    public TaskRunExecutionService(
            TaskRunService taskRunService,
            TaskLeaseTransaction leaseTransaction,
            TaskRunProperties properties,
            Clock clock,
            @Qualifier("taskRunExecutor") TaskExecutor executor,
            TaskRunFailureLogger failureLogger) {
        this.taskRunService = taskRunService;
        this.leaseTransaction = leaseTransaction;
        this.properties = properties;
        this.clock = clock;
        this.executor = executor;
        this.failureLogger = failureLogger;
    }

    public TaskStartResult submit(TaskStartCommand command, TrackedTask task) {
        Objects.requireNonNull(task, "task must not be null");
        TaskStartResult result = taskRunService.start(command);
        if (result instanceof TaskStartResult.Created created) {
            UUID runId = created.run().getRunId();
            try {
                executor.execute(() -> execute(runId, task));
            } catch (RejectedExecutionException exception) {
                TaskRunTerminalSnapshot snapshot = taskRunService.failQueued(
                        runId, EXECUTOR_REJECTED, exception.getMessage());
                logFailure(snapshot);
            }
        }
        return result;
    }

    private void execute(UUID runId, TrackedTask task) {
        TaskLease lease;
        try {
            lease = new TaskLease(runId, taskRunService.markRunning(runId));
        } catch (BusinessException exception) {
            log.info("Task run {} could not start: {}", runId, exception.getErrorCode());
            return;
        }

        ThrottledTaskProgressReporter reporter = new ThrottledTaskProgressReporter(
                lease, leaseTransaction, properties.progress(), clock);
        TaskRunTerminalSnapshot terminalSnapshot;
        try {
            task.execute(new TaskExecutionContext(lease, reporter));
            reporter.flush();
            terminalSnapshot = taskRunService.complete(runId, lease.token());
        } catch (Exception exception) {
            try {
                reporter.flush();
            } catch (BusinessException transitionFailure) {
                log.info("Task run {} no longer owns its progress flush: {}",
                        runId, transitionFailure.getErrorCode());
                return;
            }
            terminalSnapshot = failRunning(lease, exception);
        }

        if (terminalSnapshot != null) {
            logFailure(terminalSnapshot);
            notifyTerminal(task, new TaskTerminalContext(runId, terminalSnapshot.status()));
        }
    }

    private TaskRunTerminalSnapshot failRunning(TaskLease lease, Exception failure) {
        try {
            String errorType = failure instanceof BusinessException businessException
                    ? businessException.getErrorCode().name()
                    : failure.getClass().getSimpleName();
            return taskRunService.fail(
                    lease.runId(),
                    lease.token(),
                    errorType,
                    failure.getMessage());
        } catch (BusinessException transitionFailure) {
            log.info("Task run {} no longer owns its terminal transition: {}",
                    lease.runId(), transitionFailure.getErrorCode());
            return null;
        }
    }

    private void logFailure(TaskRunTerminalSnapshot snapshot) {
        if (snapshot.status() == TaskRunStatus.SUCCEEDED) {
            return;
        }
        try {
            failureLogger.log(snapshot);
        } catch (Exception exception) {
            log.error("Task run {} failure event logging failed", snapshot.runId(), exception);
        }
    }

    private void notifyTerminal(TrackedTask task, TaskTerminalContext context) {
        try {
            task.afterTerminal(context);
        } catch (Exception exception) {
            log.error("Task run {} terminal callback failed", context.runId(), exception);
        }
    }
}
