package com.fuma.hiselectors.taskrun.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fuma.hiselectors.config.CacheConfig;
import com.fuma.hiselectors.taskrun.config.TaskExecutorConfig;
import com.fuma.hiselectors.taskrun.config.TaskRunProperties;
import com.fuma.hiselectors.taskrun.config.TaskTypePolicy;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskRunStatus;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import com.fuma.hiselectors.taskrun.repository.TaskRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
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

    @BeforeEach
    void clearRuns() {
        repository.deleteAll();
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
        return new TaskRunExecutionService(service, leaseTransaction, properties, clock, executor);
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
