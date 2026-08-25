package com.fuma.hiselectors.taskrun.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fuma.hiselectors.config.CacheConfig;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.settlement.dto.SettlementRecalculationResponse;
import com.fuma.hiselectors.settlement.service.SettlementRecalculationService;
import com.fuma.hiselectors.settlement.task.SettlementRecalculationTask;
import com.fuma.hiselectors.taskrun.config.TaskExecutorConfig;
import com.fuma.hiselectors.taskrun.config.TaskRunProperties;
import com.fuma.hiselectors.taskrun.config.TaskTypePolicy;
import com.fuma.hiselectors.taskrun.logging.TaskRunFailureLogger;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskRunStatus;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import com.fuma.hiselectors.taskrun.repository.TaskRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "task-run.progress.flush-count=10"
})
@Import({
        CacheConfig.class,
        RequestFingerprint.class,
        TaskRunCreator.class,
        TaskRunConflictResolver.class,
        TaskTypePolicy.class,
        TaskRunService.class,
        TaskLeaseTransaction.class,
        TaskRunExecutionServiceTest.FixedConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TaskRunExecutionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-23T03:00:00Z");

    @Autowired
    private TaskRunService service;

    @Autowired
    private TaskLeaseTransaction leaseTransaction;

    @Autowired
    private TaskRunRepository repository;

    @Autowired
    private TaskRunProperties properties;

    @Autowired
    private Clock clock;

    @Autowired
    private ObjectMapper objectMapper;

    private ThreadPoolTaskExecutor configuredExecutor;
    private final TaskRunFailureLogger failureLogger = mock(TaskRunFailureLogger.class);
    private final TaskRunProgressStream progressStream = mock(TaskRunProgressStream.class);

    @BeforeEach
    void clearRuns() {
        repository.deleteAll();
        reset(failureLogger, progressStream);
    }

    @Test
    void workerReporterPublishesContentStepSnapshotWithCreatedRunId() {
        TaskStartResult result = taskRunExecutionService(Runnable::run).submit(
                command(UUID.randomUUID()),
                context -> context.progress().reportStep("NEW_CONTENT_SYNC", null, 1));

        verify(progressStream).publish(new TaskRunProgressEvent(
                runId(result), "NEW_CONTENT_SYNC", null, 1));
    }

    @AfterEach
    void shutDownExecutor() {
        if (configuredExecutor != null) {
            configuredExecutor.shutdown();
        }
    }

    @Test
    void workerMovesQueuedRunThroughRunningAndFlushesFinalCountsBeforeCompleting() {
        AtomicBoolean observedRunning = new AtomicBoolean();
        AtomicReference<TaskTerminalContext> terminal = new AtomicReference<>();
        TrackedTask task = new TrackedTask() {
            @Override
            public void execute(TaskExecutionContext context) {
                observedRunning.set(find(context.lease().runId()).getStatus() == TaskRunStatus.RUNNING);
                context.progress().start("SYNC", 3);
                context.progress().advance(2, 0, 1);
            }

            @Override
            public void afterTerminal(TaskTerminalContext context) {
                terminal.set(context);
                assertThat(find(context.runId()).getStatus()).isEqualTo(TaskRunStatus.SUCCEEDED);
            }
        };

        TaskStartResult result = taskRunExecutionService(Runnable::run).submit(command(UUID.randomUUID()), task);

        UUID runId = ((TaskStartResult.Created) result).run().getRunId();
        TaskRun run = find(runId);
        assertThat(observedRunning).isTrue();
        assertThat(run.getStatus()).isEqualTo(TaskRunStatus.SUCCEEDED);
        assertThat(run.getProcessedCount()).isEqualTo(3);
        assertThat(run.getSucceededCount()).isEqualTo(2);
        assertThat(run.getSkippedCount()).isEqualTo(1);
        assertThat(terminal.get()).isEqualTo(new TaskTerminalContext(runId, TaskRunStatus.SUCCEEDED));
        verify(failureLogger, never()).log(any());
    }

    @Test
    void reportedFailuresChoosePartialOrFailedTerminalStatus() {
        TaskStartResult partial = taskRunExecutionService(Runnable::run).submit(
                command(UUID.randomUUID()),
                context -> context.progress().advance(1, 1, 0));
        TaskStartResult failed = taskRunExecutionService(Runnable::run).submit(
                command(UUID.randomUUID()),
                context -> context.progress().advance(0, 2, 0));

        assertThat(find(runId(partial)).getStatus()).isEqualTo(TaskRunStatus.PARTIAL_FAILED);
        assertThat(find(runId(partial)).getProcessedCount()).isEqualTo(2);
        assertThat(find(runId(failed)).getStatus()).isEqualTo(TaskRunStatus.FAILED);
        assertThat(find(runId(failed)).getFailedCount()).isEqualTo(2);
        verify(failureLogger, times(2)).log(any());
    }

    @Test
    void flushesAMessageOnlyReportBeforeCompleting() {
        TaskStartResult result = taskRunExecutionService(Runnable::run).submit(
                command(UUID.randomUUID()),
                context -> context.progress().describe("YouTube 7명 · Instagram 4명 수집"));

        TaskRun run = find(runId(result));
        assertThat(run.getProgressMessage()).isEqualTo("YouTube 7명 · Instagram 4명 수집");
        assertThat(run.getStatus()).isEqualTo(TaskRunStatus.SUCCEEDED);
    }

    @Test
    void settlementRecalculationPersistsDetailsWhenTheCountThresholdFlushesProgress() {
        SettlementRecalculationResponse response = new SettlementRecalculationResponse(
                null, YearMonth.of(2026, 7), YearMonth.of(2026, 2), YearMonth.of(2026, 7),
                2, 6, 4, 3, 2, 2, 1);

        TaskRun run = executeSettlementRecalculation(response, persisted ->
                assertSettlementProgress(persisted, response, TaskRunStatus.RUNNING));

        assertSettlementProgress(run, response, TaskRunStatus.PARTIAL_FAILED);
    }

    @Test
    void settlementRecalculationPersistsDetailsDuringTheFinalFlush() {
        SettlementRecalculationResponse response = new SettlementRecalculationResponse(
                null, YearMonth.of(2026, 7), YearMonth.of(2026, 5), YearMonth.of(2026, 7),
                2, 3, 1, 2, 1, 1, 1);

        TaskRun run = executeSettlementRecalculation(response, persisted -> {
            assertThat(persisted.getStatus()).isEqualTo(TaskRunStatus.RUNNING);
            assertThat(persisted.getProgressMessage()).isNull();
            assertThat(persisted.getTotalCount()).isEqualTo(6);
            assertThat(persisted.getProcessedCount()).isZero();
            assertThat(persisted.getSucceededCount()).isZero();
            assertThat(persisted.getFailedCount()).isZero();
            assertThat(persisted.getSkippedCount()).isZero();
        });

        assertSettlementProgress(run, response, TaskRunStatus.PARTIAL_FAILED);
    }

    @Test
    void uncaughtTaskFailureFailsTheRunningRunAndStillInvokesTerminalCallback() {
        AtomicReference<TaskTerminalContext> terminal = new AtomicReference<>();
        TrackedTask task = new TrackedTask() {
            @Override
            public void execute(TaskExecutionContext context) {
                context.progress().advance(1, 0, 0);
                throw new IllegalStateException("broken");
            }

            @Override
            public void afterTerminal(TaskTerminalContext context) {
                terminal.set(context);
            }
        };

        TaskStartResult result = taskRunExecutionService(Runnable::run).submit(command(UUID.randomUUID()), task);

        TaskRun run = find(runId(result));
        assertThat(run.getStatus()).isEqualTo(TaskRunStatus.FAILED);
        assertThat(run.getSucceededCount()).isEqualTo(1);
        assertThat(run.getErrorType()).isEqualTo("IllegalStateException");
        assertThat(run.getErrorMessage()).isEqualTo("broken");
        assertThat(terminal.get().status()).isEqualTo(TaskRunStatus.FAILED);
        verify(failureLogger).log(org.mockito.ArgumentMatchers.argThat(snapshot ->
                snapshot.runId().equals(run.getRunId())
                        && snapshot.status() == TaskRunStatus.FAILED
                        && "IllegalStateException".equals(snapshot.errorType())
                        && "broken".equals(snapshot.errorMessage())));
    }

    @Test
    void uncaughtBusinessFailurePersistsErrorCodeAndPublicMessageInRunAndFailureLog() {
        ErrorCode errorCode = ErrorCode.AI_CONTENT_INSPECTION_QUOTA_EXCEEDED;

        TaskStartResult result = taskRunExecutionService(Runnable::run).submit(
                command(UUID.randomUUID()),
                context -> {
                    throw new BusinessException(errorCode);
                });

        TaskRun run = find(runId(result));
        assertThat(run.getStatus()).isEqualTo(TaskRunStatus.FAILED);
        assertThat(run.getErrorType()).isEqualTo(errorCode.name());
        assertThat(run.getErrorMessage()).isEqualTo(errorCode.getMessage());
        verify(failureLogger).log(org.mockito.ArgumentMatchers.argThat(snapshot ->
                snapshot.runId().equals(run.getRunId())
                        && snapshot.status() == TaskRunStatus.FAILED
                        && errorCode.name().equals(snapshot.errorType())
                        && errorCode.getMessage().equals(snapshot.errorMessage())));
    }

    @Test
    void configuredExecutorIsBoundedNamedAndRejecting() throws Exception {
        TaskRunProperties.Executor executorProperties = properties.executor();
        configuredExecutor = new TaskExecutorConfig(properties).taskRunExecutor();
        configuredExecutor.initialize();
        AtomicReference<String> threadName = new AtomicReference<>();
        CountDownLatch executed = new CountDownLatch(1);

        configuredExecutor.execute(() -> {
            threadName.set(Thread.currentThread().getName());
            executed.countDown();
        });

        assertThat(executed.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(configuredExecutor.getCorePoolSize()).isEqualTo(executorProperties.coreSize());
        assertThat(configuredExecutor.getMaxPoolSize()).isEqualTo(executorProperties.maxSize());
        assertThat(configuredExecutor.getQueueCapacity()).isEqualTo(executorProperties.queueCapacity());
        assertThat(threadName.get()).startsWith(executorProperties.threadPrefix());
        assertThat(configuredExecutor.getThreadPoolExecutor().getRejectedExecutionHandler())
                .isInstanceOf(java.util.concurrent.ThreadPoolExecutor.AbortPolicy.class);
    }

    @Test
    void executorRejectionFailsQueuedRunButReturnsTheCreatedRunId() {
        TaskExecutor rejecting = task -> {
            throw new TaskRejectedException("queue full");
        };

        TaskStartResult result = taskRunExecutionService(rejecting).submit(command(UUID.randomUUID()), context -> { });

        assertThat(result).isInstanceOf(TaskStartResult.Created.class);
        TaskRun run = find(runId(result));
        assertThat(run.getStatus()).isEqualTo(TaskRunStatus.FAILED);
        assertThat(run.getErrorType()).isEqualTo("EXECUTOR_REJECTED");
        verify(failureLogger).log(org.mockito.ArgumentMatchers.argThat(snapshot ->
                snapshot.runId().equals(run.getRunId())
                        && snapshot.status() == TaskRunStatus.FAILED));
    }

    @Test
    void replayAndActiveConflictDoNotSubmitAnotherTask() throws Exception {
        CapturingExecutor executor = new CapturingExecutor();
        TaskRunExecutionService taskRunExecutionService = taskRunExecutionService(executor);
        UUID key = UUID.randomUUID();
        TrackedTask replayedTask = mock(TrackedTask.class);
        TrackedTask conflictedTask = mock(TrackedTask.class);

        TaskStartResult created = taskRunExecutionService.submit(command(key), context -> { });
        TaskStartResult replayed = taskRunExecutionService.submit(command(key), replayedTask);
        TaskStartResult conflict = taskRunExecutionService.submit(command(UUID.randomUUID()), conflictedTask);

        assertThat(created).isInstanceOf(TaskStartResult.Created.class);
        assertThat(replayed).isInstanceOf(TaskStartResult.Replayed.class);
        assertThat(conflict).isInstanceOf(TaskStartResult.ActiveConflict.class);
        assertThat(executor.tasks).hasSize(1);
        verify(replayedTask, never()).execute(org.mockito.ArgumentMatchers.any());
        verify(conflictedTask, never()).execute(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void lostLeaseLeavesStaleRunUntouchedAndSkipsTerminalCallback() {
        AtomicBoolean callback = new AtomicBoolean();
        TrackedTask task = new TrackedTask() {
            @Override
            public void execute(TaskExecutionContext context) {
                context.progress().advance(1, 0, 0);
                service.markStale(context.lease().runId(), UUID.randomUUID(), true);
            }

            @Override
            public void afterTerminal(TaskTerminalContext context) {
                callback.set(true);
            }
        };

        TaskStartResult result = taskRunExecutionService(Runnable::run).submit(command(UUID.randomUUID()), task);

        assertThat(find(runId(result)).getStatus()).isEqualTo(TaskRunStatus.STALE);
        assertThat(callback).isFalse();
        verify(failureLogger, never()).log(any());
    }

    @Test
    void failureLoggingRunsAfterCommitAndLoggerExceptionsCannotChangeTerminalState() {
        AtomicBoolean observedCommitted = new AtomicBoolean();
        doAnswer(invocation -> {
            TaskRunTerminalSnapshot snapshot = invocation.getArgument(0);
            observedCommitted.set(find(snapshot.runId()).getStatus() == TaskRunStatus.PARTIAL_FAILED);
            throw new IllegalStateException("logging unavailable");
        }).when(failureLogger).log(any());

        TaskStartResult result = taskRunExecutionService(Runnable::run).submit(
                command(UUID.randomUUID()),
                context -> context.progress().advance(1, 1, 0));

        assertThat(observedCommitted).isTrue();
        assertThat(find(runId(result)).getStatus()).isEqualTo(TaskRunStatus.PARTIAL_FAILED);
    }

    @Test
    void executorRejectionLoggingFailureCannotRollBackQueuedFailure() {
        doThrow(new IllegalStateException("logging unavailable")).when(failureLogger).log(any());
        TaskExecutor rejecting = task -> {
            throw new TaskRejectedException("queue full");
        };

        TaskStartResult result = taskRunExecutionService(rejecting).submit(
                command(UUID.randomUUID()), context -> { });

        assertThat(find(runId(result)).getStatus()).isEqualTo(TaskRunStatus.FAILED);
    }

    @Test
    void callbackFailureCannotChangeCommittedTerminalState() {
        TrackedTask task = new TrackedTask() {
            @Override
            public void execute(TaskExecutionContext context) {
                context.progress().advance(1, 0, 0);
            }

            @Override
            public void afterTerminal(TaskTerminalContext context) {
                assertThat(find(context.runId()).getStatus()).isEqualTo(TaskRunStatus.SUCCEEDED);
                throw new IllegalArgumentException("callback failed");
            }
        };

        TaskStartResult result = taskRunExecutionService(Runnable::run).submit(command(UUID.randomUUID()), task);

        assertThat(find(runId(result)).getStatus()).isEqualTo(TaskRunStatus.SUCCEEDED);
    }

    private TaskRunExecutionService taskRunExecutionService(TaskExecutor executor) {
        return new TaskRunExecutionService(
                service,
                leaseTransaction,
                properties,
                clock,
                executor,
                failureLogger,
                progressStream);
    }

    private TaskRun executeSettlementRecalculation(
            SettlementRecalculationResponse response, Consumer<TaskRun> afterExecuteProbe) {
        SettlementRecalculationService recalculationService = mock(SettlementRecalculationService.class);
        YearMonth activityMonth = response.requestedActivityMonth();
        org.mockito.Mockito.when(recalculationService.recalculate(activityMonth, null, true))
                .thenReturn(response);
        SettlementRecalculationTask task = new SettlementRecalculationTask(
                recalculationService, activityMonth, null, true) {
            @Override
            public void execute(TaskExecutionContext context) {
                super.execute(context);
                afterExecuteProbe.accept(find(context.lease().runId()));
            }
        };

        TaskStartResult result = taskRunExecutionService(Runnable::run)
                .submit(settlementCommand(UUID.randomUUID(), activityMonth), task);

        return find(runId(result));
    }

    private void assertSettlementProgress(
            TaskRun run,
            SettlementRecalculationResponse response,
            TaskRunStatus expectedStatus) {
        assertThat(run.getStatus()).isEqualTo(expectedStatus);
        assertThat(run.getProgressMessage()).isEqualTo(String.format(
                "신규 %d건 · 수정 %d건 · 확정 %d건 · 실패 %d건 · 건너뜀 %d건",
                response.createdCount(),
                response.updatedCount(),
                response.finalizedCount(),
                response.failedCount(),
                response.skippedCount()));
        assertThat(run.getTotalCount())
                .isEqualTo((long) response.selectorsCount() * response.activityMonthsCount());
        assertThat(run.getProcessedCount()).isEqualTo(
                response.createdCount()
                        + response.updatedCount()
                        + response.finalizedCount()
                        + response.failedCount()
                        + response.skippedCount());
        assertThat(run.getSucceededCount()).isEqualTo(
                response.createdCount() + response.updatedCount() + response.finalizedCount());
        assertThat(run.getFailedCount()).isEqualTo(response.failedCount());
        assertThat(run.getSkippedCount()).isEqualTo(response.skippedCount());
    }

    private TaskStartCommand settlementCommand(UUID key, YearMonth activityMonth) {
        var payload = objectMapper.createObjectNode();
        payload.put("activityMonth", activityMonth.toString());
        payload.putNull("selectorsId");
        payload.put("force", true);
        return new TaskStartCommand(
                TaskType.SETTLEMENT_CALCULATION,
                TriggerType.ADMIN_TRIGGERED,
                42L,
                key,
                payload);
    }

    private TaskStartCommand command(UUID key) {
        JsonNode payload = objectMapper.createObjectNode().put("batch", 1);
        return new TaskStartCommand(TaskType.CONTENT_SYNC, TriggerType.SCHEDULED, null, key, payload);
    }

    private UUID runId(TaskStartResult result) {
        return ((TaskStartResult.Created) result).run().getRunId();
    }

    private TaskRun find(UUID runId) {
        return repository.findByRunId(runId).orElseThrow();
    }

    private static final class CapturingExecutor implements TaskExecutor {

        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            tasks.add(task);
        }
    }

    @TestConfiguration
    @EnableConfigurationProperties(TaskRunProperties.class)
    static class FixedConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
