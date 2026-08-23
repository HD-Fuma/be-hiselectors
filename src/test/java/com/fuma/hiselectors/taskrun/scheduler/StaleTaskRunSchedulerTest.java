package com.fuma.hiselectors.taskrun.scheduler;

import static com.fuma.hiselectors.exception.ErrorCode.TASK_RUN_LEASE_LOST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fuma.hiselectors.config.CacheConfig;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.taskrun.config.TaskRunProperties;
import com.fuma.hiselectors.taskrun.config.TaskTypePolicy;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskRunStatus;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import com.fuma.hiselectors.taskrun.repository.TaskRunRepository;
import com.fuma.hiselectors.taskrun.service.TaskLease;
import com.fuma.hiselectors.taskrun.service.TaskLeaseTransaction;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({
        CacheConfig.class,
        TaskLeaseTransaction.class,
        StaleTaskRunSchedulerTest.FixedClockConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StaleTaskRunSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-08-23T03:00:00Z");

    @Autowired
    private TaskRunRepository repository;

    @Autowired
    private TaskLeaseTransaction leaseTransaction;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactions;
    private TaskTypePolicy policy;
    private StaleTaskRunScheduler scheduler;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        transactions = new TransactionTemplate(transactionManager);
        TaskRunProperties properties = properties();
        policy = new TaskTypePolicy(properties);
        scheduler = new StaleTaskRunScheduler(
                repository,
                policy,
                transactions,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void policyRegistryHasTheExactSingletonRecoveryAndTimeoutRules() {
        assertThat(policy.forType(TaskType.CREATOR_SYNC).singleton()).isTrue();
        assertThat(policy.forType(TaskType.CONTENT_SYNC).singleton()).isTrue();
        assertThat(policy.forType(TaskType.APPLICATION_REPORT_GENERATION).singleton()).isTrue();
        assertThat(policy.forType(TaskType.CONTENT_REPORT_GENERATION).singleton()).isTrue();
        assertThat(policy.forType(TaskType.SETTLEMENT_CALCULATION).singleton()).isTrue();
        assertThat(policy.forType(TaskType.KAKAO_MESSAGE_SEND).singleton()).isFalse();
        assertThat(policy.forType(TaskType.PROPOSAL_EMAIL_SEND).singleton()).isFalse();
        assertThat(policy.forType(TaskType.CREATOR_SYNC).staleTimeout()).isEqualTo(Duration.ofMinutes(30));
        assertThat(policy.forType(TaskType.CONTENT_SYNC).staleTimeout()).isEqualTo(Duration.ofMinutes(30));
        assertThat(policy.forType(TaskType.APPLICATION_REPORT_GENERATION).staleTimeout())
                .isEqualTo(Duration.ofMinutes(120));
        assertThat(policy.forType(TaskType.CONTENT_REPORT_GENERATION).staleTimeout())
                .isEqualTo(Duration.ofMinutes(120));
        assertThat(policy.forType(TaskType.SETTLEMENT_CALCULATION).staleTimeout())
                .isEqualTo(Duration.ofMinutes(30));
        assertThat(policy.forType(TaskType.KAKAO_MESSAGE_SEND).staleTimeout()).isEqualTo(Duration.ofMinutes(10));
        assertThat(policy.forType(TaskType.PROPOSAL_EMAIL_SEND).staleTimeout()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void rejectsNonPositiveStaleTimeouts() {
        TaskRunProperties.Timeouts valid = properties().stale().timeouts();

        assertThatThrownBy(() -> new TaskRunProperties.Timeouts(
                Duration.ZERO,
                valid.contentSync(),
                valid.applicationReportGeneration(),
                valid.contentReportGeneration(),
                valid.settlementCalculation(),
                valid.kakaoMessageSend(),
                valid.proposalEmailSend()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void marksQueuedAndRunningCandidatesStaleAndReleasesSingletonForRecovery() {
        TaskRun queued = queued(TaskType.CREATOR_SYNC, NOW.minus(Duration.ofMinutes(31)));
        TaskRun running = running(TaskType.KAKAO_MESSAGE_SEND, NOW.minus(Duration.ofMinutes(11)));
        UUID oldToken = running.getLeaseToken();
        TaskRun fresh = running(TaskType.CONTENT_SYNC, NOW.minus(Duration.ofMinutes(29)));

        scheduler.markStaleRuns();

        TaskRun staleQueued = find(queued);
        TaskRun staleRunning = find(running);
        assertThat(staleQueued.getStatus()).isEqualTo(TaskRunStatus.STALE);
        assertThat(staleQueued.getConcurrencyKey()).isNull();
        assertThat(staleRunning.getStatus()).isEqualTo(TaskRunStatus.STALE);
        assertThat(staleRunning.getLeaseToken()).isNotEqualTo(oldToken);
        assertThat(find(fresh).getStatus()).isEqualTo(TaskRunStatus.RUNNING);

        TaskRun replacement = queued(TaskType.CREATOR_SYNC, NOW);
        assertThat(replacement.getStatus()).isEqualTo(TaskRunStatus.QUEUED);
    }

    @Test
    void workerLockFirstRefreshesHeartbeatBeforeTheSchedulerRechecksCutoff() throws Exception {
        TaskRun run = running(TaskType.CONTENT_SYNC, NOW.minus(Duration.ofMinutes(31)));
        CountDownLatch workerLocked = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        CountDownLatch schedulerStarted = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> worker = executor.submit(() -> transactions.executeWithoutResult(ignored -> {
                TaskRun locked = repository.findByRunIdForUpdate(run.getRunId()).orElseThrow();
                workerLocked.countDown();
                await(releaseWorker);
                locked.addCounts(0, 0, 0, NOW);
            }));
            await(workerLocked);
            Future<?> staleScan = executor.submit(() -> {
                schedulerStarted.countDown();
                scheduler.markStaleRuns();
            });
            await(schedulerStarted);
            assertBlocked(staleScan);
            releaseWorker.countDown();
            get(worker);
            get(staleScan);
        }

        assertThat(find(run).getStatus()).isEqualTo(TaskRunStatus.RUNNING);
        assertThat(find(run).getHeartbeatAt()).isEqualTo(NOW);
    }

    @Test
    void staleLockFirstRotatesTheTokenAndRejectsOldWorkerPersistence() throws Exception {
        TaskRun run = running(TaskType.CONTENT_SYNC, NOW.minus(Duration.ofMinutes(31)));
        TaskLease oldLease = new TaskLease(run.getRunId(), run.getLeaseToken());
        UUID replacementToken = UUID.randomUUID();
        CountDownLatch staleLocked = new CountDownLatch(1);
        CountDownLatch releaseStale = new CountDownLatch(1);
        CountDownLatch workerStarted = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> stale = executor.submit(() -> transactions.executeWithoutResult(ignored -> {
                TaskRun locked = repository.findByRunIdForUpdate(run.getRunId()).orElseThrow();
                locked.markStale(replacementToken, true, NOW);
                staleLocked.countDown();
                await(releaseStale);
            }));
            await(staleLocked);
            Future<?> worker = executor.submit(() -> {
                workerStarted.countDown();
                leaseTransaction.execute(oldLease, 1, 0, 0, () -> {
                });
            });
            await(workerStarted);
            assertBlocked(worker);
            releaseStale.countDown();
            get(stale);

            assertThatThrownByFuture(worker);
        }

        TaskRun stale = find(run);
        assertThat(stale.getStatus()).isEqualTo(TaskRunStatus.STALE);
        assertThat(stale.getLeaseToken()).isEqualTo(replacementToken);
        assertThat(stale.getProcessedCount()).isZero();
    }

    private void assertThatThrownByFuture(Future<?> worker) {
        try {
            worker.get(5, TimeUnit.SECONDS);
            throw new AssertionError("old worker persistence was accepted");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        } catch (ExecutionException exception) {
            assertThat(exception.getCause())
                    .isInstanceOfSatisfying(BusinessException.class,
                            businessException -> assertThat(businessException.getErrorCode())
                                    .isEqualTo(TASK_RUN_LEASE_LOST));
        } catch (TimeoutException exception) {
            throw new AssertionError("old worker did not finish", exception);
        }
    }

    private void assertBlocked(Future<?> future) {
        assertThatThrownBy(() -> future.get(200, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);
    }

    private void get(Future<?> future) {
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new AssertionError(exception);
        }
    }

    private TaskRun queued(TaskType type, Instant heartbeatAt) {
        return repository.saveAndFlush(TaskRun.queued(
                type,
                TriggerType.SCHEDULED,
                null,
                UUID.randomUUID(),
                "fingerprint-" + UUID.randomUUID(),
                policy.forType(type).singleton() ? type.name() : null,
                heartbeatAt));
    }

    private TaskRun running(TaskType type, Instant heartbeatAt) {
        TaskRun run = TaskRun.queued(
                type,
                TriggerType.SCHEDULED,
                null,
                UUID.randomUUID(),
                "fingerprint-" + UUID.randomUUID(),
                policy.forType(type).singleton() ? type.name() : null,
                heartbeatAt);
        run.markRunning(UUID.randomUUID(), heartbeatAt);
        return repository.saveAndFlush(run);
    }

    private TaskRun find(TaskRun run) {
        return repository.findByRunId(run.getRunId()).orElseThrow();
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("latch timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private TaskRunProperties properties() {
        return new TaskRunProperties(
                new TaskRunProperties.Progress(10, 2_000),
                new TaskRunProperties.Stale(
                        60_000,
                        new TaskRunProperties.Timeouts(
                                Duration.ofMinutes(30),
                                Duration.ofMinutes(30),
                                Duration.ofMinutes(120),
                                Duration.ofMinutes(120),
                                Duration.ofMinutes(30),
                                Duration.ofMinutes(10),
                                Duration.ofMinutes(10))));
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
