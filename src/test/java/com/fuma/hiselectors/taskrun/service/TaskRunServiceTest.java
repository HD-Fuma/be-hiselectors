package com.fuma.hiselectors.taskrun.service;

import static com.fuma.hiselectors.exception.ErrorCode.IDEMPOTENCY_KEY_REUSED;
import static com.fuma.hiselectors.exception.ErrorCode.INVALID_TASK_RUN_TRANSITION;
import static com.fuma.hiselectors.exception.ErrorCode.TASK_RUN_LEASE_LOST;
import static com.fuma.hiselectors.exception.ErrorCode.TASK_RUN_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.config.CacheConfig;
import com.fuma.hiselectors.exception.BusinessException;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.OptimisticLockingFailureException;
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
        TaskRunServiceTest.FixedClockConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TaskRunServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-23T03:00:00Z");

    @Autowired
    private TaskRunService service;

    @Autowired
    private TaskRunRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearRuns() {
        repository.deleteAll();
    }

    @Test
    void createsANewQueuedRun() {
        TaskStartCommand command = command(TaskType.CONTENT_SYNC, UUID.randomUUID(), payload(1));

        TaskStartResult result = service.start(command);

        assertThat(result).isInstanceOf(TaskStartResult.Created.class);
        TaskRun run = ((TaskStartResult.Created) result).run();
        assertThat(run.getStatus()).isEqualTo(TaskRunStatus.QUEUED);
        assertThat(run.getTaskType()).isEqualTo(TaskType.CONTENT_SYNC);
        assertThat(run.getConcurrencyKey()).isEqualTo(TaskType.CONTENT_SYNC.name());
        assertThat(run.getHeartbeatAt()).isEqualTo(NOW);
    }

    @Test
    void sameIdempotencyKeyAndFingerprintReplaysTheOriginalRun() {
        UUID key = UUID.randomUUID();
        TaskStartResult.Created created = (TaskStartResult.Created) service.start(
                command(TaskType.CONTENT_SYNC, key, payload(1)));

        TaskStartResult result = service.start(command(TaskType.CONTENT_SYNC, key, payload(1)));

        assertThat(result).isInstanceOf(TaskStartResult.Replayed.class);
        assertThat(((TaskStartResult.Replayed) result).run().getRunId())
                .isEqualTo(created.run().getRunId());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void sameIdempotencyKeyWithDifferentFingerprintIsRejected() {
        UUID key = UUID.randomUUID();
        service.start(command(TaskType.CONTENT_SYNC, key, payload(1)));

        assertThatThrownBy(() -> service.start(command(TaskType.CONTENT_SYNC, key, payload(2))))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(IDEMPOTENCY_KEY_REUSED));
    }

    @Test
    void singletonTaskReturnsTheActiveRunAsAConflict() {
        TaskStartResult.Created active = (TaskStartResult.Created) service.start(
                command(TaskType.SETTLEMENT_CALCULATION, UUID.randomUUID(), payload(1)));

        TaskStartResult result = service.start(
                command(TaskType.SETTLEMENT_CALCULATION, UUID.randomUUID(), payload(2)));

        assertThat(result).isInstanceOf(TaskStartResult.ActiveConflict.class);
        assertThat(((TaskStartResult.ActiveConflict) result).activeRun().getRunId())
                .isEqualTo(active.run().getRunId());
    }

    @Test
    void distinctKakaoAndProposalSendRequestsCanRunConcurrently() {
        List<TaskStartResult> results = List.of(
                service.start(command(TaskType.KAKAO_MESSAGE_SEND, UUID.randomUUID(), payload(1))),
                service.start(command(TaskType.KAKAO_MESSAGE_SEND, UUID.randomUUID(), payload(2))),
                service.start(command(TaskType.PROPOSAL_EMAIL_SEND, UUID.randomUUID(), payload(1))),
                service.start(command(TaskType.PROPOSAL_EMAIL_SEND, UUID.randomUUID(), payload(2))));

        assertThat(results).allMatch(TaskStartResult.Created.class::isInstance);
        assertThat(repository.findAll()).allMatch(run -> run.getConcurrencyKey() == null);
    }

    @Test
    void simultaneousSameRequestsCreateOnceAndReplayOnce() throws Exception {
        UUID key = UUID.randomUUID();
        TaskStartCommand command = command(TaskType.KAKAO_MESSAGE_SEND, key, payload(1));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<TaskStartResult> first = executor.submit(() -> startTogether(command, ready, start));
            Future<TaskStartResult> second = executor.submit(() -> startTogether(command, ready, start));
            ready.await();
            start.countDown();

            List<TaskStartResult> results = List.of(first.get(), second.get());
            assertThat(results).filteredOn(TaskStartResult.Created.class::isInstance).hasSize(1);
            assertThat(results).filteredOn(TaskStartResult.Replayed.class::isInstance).hasSize(1);
        }
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void simultaneousDifferentSingletonRequestsCreateOnceAndConflictOnce() throws Exception {
        TaskStartCommand firstCommand = command(
                TaskType.CONTENT_REPORT_GENERATION, UUID.randomUUID(), payload(1));
        TaskStartCommand secondCommand = command(
                TaskType.CONTENT_REPORT_GENERATION, UUID.randomUUID(), payload(2));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<TaskStartResult> first = executor.submit(
                    () -> startTogether(firstCommand, ready, start));
            Future<TaskStartResult> second = executor.submit(
                    () -> startTogether(secondCommand, ready, start));
            ready.await();
            start.countDown();

            List<TaskStartResult> results = List.of(first.get(), second.get());
            assertThat(results).filteredOn(TaskStartResult.Created.class::isInstance).hasSize(1);
            assertThat(results).filteredOn(TaskStartResult.ActiveConflict.class::isInstance).hasSize(1);
        }
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void runningRunCompletesOnceAndKeepsTheFirstTerminalStatus() {
        TaskRun run = createdRun(TaskType.CONTENT_SYNC);
        UUID leaseToken = service.markRunning(run.getRunId());

        service.complete(run.getRunId(), leaseToken);

        assertThat(repository.findByRunId(run.getRunId())).get()
                .extracting(TaskRun::getStatus).isEqualTo(TaskRunStatus.SUCCEEDED);
        assertThatThrownBy(() -> service.fail(run.getRunId(), leaseToken, "LATE", "too late"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(INVALID_TASK_RUN_TRANSITION));
        assertThat(repository.findByRunId(run.getRunId())).get()
                .extracting(TaskRun::getStatus).isEqualTo(TaskRunStatus.SUCCEEDED);
    }

    @Test
    void completeReturnsAnImmutableTerminalSnapshotAfterFlush() {
        TaskRun created = createdRun(TaskType.CONTENT_SYNC);
        UUID leaseToken = service.markRunning(created.getRunId());
        TaskRun running = repository.findByRunId(created.getRunId()).orElseThrow();
        running.setTotal(4, NOW);
        running.addCounts(2, 1, 1, NOW);
        repository.saveAndFlush(running);

        TaskRunTerminalSnapshot snapshot = service.complete(created.getRunId(), leaseToken);

        assertThat(snapshot).isEqualTo(new TaskRunTerminalSnapshot(
                created.getRunId(),
                TaskType.CONTENT_SYNC,
                TriggerType.SCHEDULED,
                TaskRunStatus.PARTIAL_FAILED,
                NOW,
                NOW,
                4L,
                4,
                2,
                1,
                1,
                null,
                null));
    }

    @Test
    void terminalTransitionRequiresTheCurrentLease() {
        TaskRun run = createdRun(TaskType.CONTENT_SYNC);
        service.markRunning(run.getRunId());

        assertThatThrownBy(() -> service.complete(run.getRunId(), UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(TASK_RUN_LEASE_LOST));
    }

    @Test
    void nullLeaseCannotFailARunningRun() {
        TaskRun run = createdRun(TaskType.CONTENT_SYNC);
        service.markRunning(run.getRunId());

        assertThatThrownBy(() -> service.fail(run.getRunId(), null, "ERROR", "message"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(TASK_RUN_LEASE_LOST));
        assertThat(repository.findByRunId(run.getRunId())).get()
                .extracting(TaskRun::getStatus).isEqualTo(TaskRunStatus.RUNNING);
    }

    @Test
    void queuedExecutorRejectionUsesAnExplicitPath() {
        TaskRun run = createdRun(TaskType.KAKAO_MESSAGE_SEND);

        TaskRunTerminalSnapshot snapshot =
                service.failQueued(run.getRunId(), "EXECUTOR_REJECTED", "queue is full");

        assertThat(repository.findByRunId(run.getRunId())).get()
                .extracting(TaskRun::getStatus).isEqualTo(TaskRunStatus.FAILED);
        assertThat(snapshot.runId()).isEqualTo(run.getRunId());
        assertThat(snapshot.status()).isEqualTo(TaskRunStatus.FAILED);
        assertThat(snapshot.startedAt()).isNull();
        assertThat(snapshot.finishedAt()).isEqualTo(NOW);
        assertThat(snapshot.errorType()).isEqualTo("EXECUTOR_REJECTED");
        assertThat(snapshot.errorMessage()).isEqualTo("queue is full");
    }

    @Test
    void queuedOnlyFailureCannotFailARunningRun() {
        TaskRun run = createdRun(TaskType.KAKAO_MESSAGE_SEND);
        service.markRunning(run.getRunId());

        assertThatThrownBy(() -> service.failQueued(run.getRunId(), "REJECTED", "too late"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(INVALID_TASK_RUN_TRANSITION));
        assertThat(repository.findByRunId(run.getRunId())).get()
                .extracting(TaskRun::getStatus).isEqualTo(TaskRunStatus.RUNNING);
    }

    @Test
    void staleTransitionKeepsOrReleasesConcurrencyKeyAsRequested() {
        TaskRun kept = createdRun(TaskType.CREATOR_SYNC);
        service.markRunning(kept.getRunId());
        service.markStale(kept.getRunId(), UUID.randomUUID(), false);

        assertThat(repository.findByRunId(kept.getRunId())).get()
                .extracting(TaskRun::getConcurrencyKey).isEqualTo(TaskType.CREATOR_SYNC.name());

        repository.deleteAll();
        TaskRun released = createdRun(TaskType.CREATOR_SYNC);
        service.markRunning(released.getRunId());
        service.markStale(released.getRunId(), UUID.randomUUID(), true);

        assertThat(repository.findByRunId(released.getRunId())).get()
                .extracting(TaskRun::getConcurrencyKey).isNull();
    }

    @Test
    void optimisticLifecycleRaceUsesAStableTransitionError() {
        TaskRunRepository racingRepository = mock(TaskRunRepository.class);
        TaskRun run = TaskRun.queued(
                TaskType.CONTENT_SYNC,
                TriggerType.SCHEDULED,
                null,
                UUID.randomUUID(),
                "fingerprint",
                TaskType.CONTENT_SYNC.name(),
                NOW);
        when(racingRepository.findByRunId(run.getRunId())).thenReturn(Optional.of(run));
        doThrow(new OptimisticLockingFailureException("lost race"))
                .when(racingRepository).flush();
        TaskRunService racingService = new TaskRunService(
                racingRepository,
                mock(TaskRunCreator.class),
                mock(TaskRunConflictResolver.class),
                mock(RequestFingerprint.class),
                mock(TaskTypePolicy.class),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> racingService.markRunning(run.getRunId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(INVALID_TASK_RUN_TRANSITION));
    }

    @Test
    void missingRunUsesStableErrorCode() {
        assertThatThrownBy(() -> service.markRunning(UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(TASK_RUN_NOT_FOUND));
    }

    @Test
    void terminalSingletonReleasesItsConcurrencyKey() {
        TaskRun first = createdRun(TaskType.CREATOR_SYNC);
        UUID leaseToken = service.markRunning(first.getRunId());
        service.complete(first.getRunId(), leaseToken);

        TaskStartResult next = service.start(
                command(TaskType.CREATOR_SYNC, UUID.randomUUID(), payload(2)));

        assertThat(next).isInstanceOf(TaskStartResult.Created.class);
    }

    private TaskStartResult startTogether(
            TaskStartCommand command,
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return service.start(command);
    }

    private TaskRun createdRun(TaskType type) {
        return ((TaskStartResult.Created) service.start(command(type, UUID.randomUUID(), payload(1)))).run();
    }

    private TaskStartCommand command(TaskType type, UUID idempotencyKey, JsonNode payload) {
        return new TaskStartCommand(type, TriggerType.SCHEDULED, null, idempotencyKey, payload);
    }

    private JsonNode payload(long generationId) {
        return objectMapper.createObjectNode().put("generationId", generationId);
    }

    @TestConfiguration
    @EnableConfigurationProperties(TaskRunProperties.class)
    static class FixedClockConfiguration {

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
