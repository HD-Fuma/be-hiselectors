package com.fuma.hiselectors.taskrun.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fuma.hiselectors.config.CacheConfig;
import com.fuma.hiselectors.taskrun.config.TaskRunProperties;
import com.fuma.hiselectors.taskrun.config.TaskTypePolicy;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskRunStatus;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import com.fuma.hiselectors.taskrun.queue.TaskQueueState.Disposition;
import com.fuma.hiselectors.taskrun.repository.TaskRunRepository;
import com.fuma.hiselectors.taskrun.service.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import tools.jackson.databind.ObjectMapper;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({CacheConfig.class, RequestFingerprint.class, TaskRunCreator.class,
        TaskRunConflictResolver.class, TaskTypePolicy.class, TaskRunService.class,
        TaskLeaseTransaction.class, TaskQueueState.class, TaskQueueStateContractTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TaskQueueStateContractTest {
    private static final Instant NOW = Instant.parse("2026-08-31T01:00:00Z");
    @Autowired TaskQueueState state;
    @Autowired TaskRunService runs;
    @Autowired TaskRunRepository repository;
    @Autowired TaskLeaseTransaction transactions;
    @Autowired MutableClock clock;
    @Autowired ObjectMapper mapper;
    @Autowired TaskQueueProperties properties;

    @BeforeEach void resetDatabase() { repository.deleteAll(); clock.now.set(NOW); }

    @Test void commandAndDispatchIntentCommitTogether() {
        var run = create(TaskType.CREATOR_SYNC);
        TaskRun saved = state.get(run.getRunId());
        assertThat(saved.isQueueManaged()).isTrue();
        assertThat(saved.getBusinessPayload()).isEqualTo("{\"source\":\"youtube-category\",\"categoryId\":7}");
        assertThat(saved.getLastEnqueuedAt()).isNull();
        assertThat(saved.getQueueAttempts()).isZero();
    }

    @Test void duplicateDeliveryCannotAcquireAnActiveLease() {
        UUID id = create(TaskType.CONTENT_SYNC).getRunId();
        var first = state.claim(id, true);
        var duplicate = state.claim(id, true);
        assertThat(first.disposition()).isEqualTo(Disposition.CLAIMED);
        assertThat(duplicate.disposition()).isEqualTo(Disposition.BUSY);
        assertThat(state.get(id).getQueueAttempts()).isEqualTo(1);
    }

    @Test void expiredSafeWorkerIsRecoveredAndOldLeaseCannotWrite() {
        UUID id = create(TaskType.CONTENT_SYNC).getRunId();
        var first = state.claim(id, true);
        clock.now.set(NOW.plusSeconds(121));
        var second = state.claim(id, true);
        assertThat(second.disposition()).isEqualTo(Disposition.CLAIMED);
        assertThat(second.lease()).isNotEqualTo(first.lease());
        assertThatThrownBy(() -> state.heartbeat(first.lease())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> transactions.execute(first.lease(), 1, 0, 0, () -> { }))
                .isInstanceOf(RuntimeException.class);
        assertThat(state.get(id).getQueueAttempts()).isEqualTo(2);
    }

    @Test void heartbeatRenewsOwnershipWithoutChangingProgress() {
        UUID id = create(TaskType.CONTENT_SYNC).getRunId();
        var claim = state.claim(id, true);
        clock.now.set(NOW.plusSeconds(100));
        state.heartbeat(claim.lease());
        clock.now.set(NOW.plusSeconds(150));
        assertThat(state.claim(id, true).disposition()).isEqualTo(Disposition.BUSY);
        assertThat(state.get(id).getProcessedCount()).isZero();
    }

    @Test void crashAfterKnownPartialSuccessDoesNotReplayCompletedItems() {
        UUID id = create(TaskType.CONTENT_SYNC).getRunId();
        var claim = state.claim(id, true);
        transactions.execute(claim.lease(), 1, 0, 0, () -> { });
        clock.now.set(NOW.plusSeconds(121));
        assertThat(state.claim(id, true).disposition()).isEqualTo(Disposition.FAILED);
        assertThat(state.get(id).getSucceededCount()).isEqualTo(1);
        assertThat(state.get(id).getQueueAttempts()).isEqualTo(1);
    }

    @Test void interruptedFinancialOrNotificationWorkRequiresReview() {
        UUID id = create(TaskType.KAKAO_MESSAGE_SEND).getRunId();
        state.claim(id, false);
        clock.now.set(NOW.plusSeconds(121));
        assertThat(state.claim(id, false).disposition()).isEqualTo(Disposition.FAILED);
        assertThat(state.get(id).getQueueAttempts()).isEqualTo(1);
        assertThat(state.get(id).getStatus()).isEqualTo(TaskRunStatus.FAILED);
    }

    @Test void retriesAreBoundedAndKeepTheSingletonUntilFinalFailure() {
        UUID id = create(TaskType.CONTENT_SYNC).getRunId();
        for (int attempt = 1; attempt <= 3; attempt++) {
            var claim = state.claim(id, true);
            assertThat(claim.disposition()).isEqualTo(Disposition.CLAIMED);
            var result = state.finish(claim.lease(), new RuntimeException("secret must not persist"), true);
            if (attempt < 3) {
                assertThat(result).isEqualTo(Disposition.RETRY);
                assertThat(runs.start(command(TaskType.CONTENT_SYNC), true))
                        .isInstanceOf(TaskStartResult.ActiveConflict.class);
                assertThat(state.claim(id, true).disposition()).isEqualTo(Disposition.BUSY);
                clock.now.set(clock.instant().plusSeconds(31));
            } else {
                assertThat(result).isEqualTo(Disposition.FAILED);
                assertThat(state.get(id).getConcurrencyKey()).isNull();
            }
        }
        assertThat(state.get(id).getErrorMessage()).doesNotContain("secret");
        assertThat(state.claim(id, true).disposition()).isEqualTo(Disposition.FAILED);
    }

    @Test void partialSuccessIsNotBlindlyReplayed() {
        UUID id = create(TaskType.CONTENT_SYNC).getRunId();
        var claim = state.claim(id, true);
        transactions.execute(claim.lease(), 1, 1, 0, () -> { });
        assertThat(state.finish(claim.lease(), null, true)).isEqualTo(Disposition.FAILED);
        assertThat(state.get(id).getStatus()).isEqualTo(TaskRunStatus.PARTIAL_FAILED);
        assertThat(state.get(id).getSucceededCount()).isEqualTo(1);
        assertThat(state.get(id).getQueueAttempts()).isEqualTo(1);
    }

    @Test void committedSuccessIsTerminalOnRedelivery() {
        UUID id = create(TaskType.CONTENT_SYNC).getRunId();
        var claim = state.claim(id, true);
        transactions.execute(claim.lease(), 1, 0, 0, () -> { });
        assertThat(state.finish(claim.lease(), null, true)).isEqualTo(Disposition.SUCCEEDED);
        assertThat(state.claim(id, true).disposition()).isEqualTo(Disposition.SUCCEEDED);
        assertThat(state.get(id).getQueueAttempts()).isEqualTo(1);
    }

    @Test void failedPublicationIsRecoveredFromDatabaseWithoutLocalExecution() {
        UUID id = create(TaskType.CONTENT_SYNC).getRunId();
        SqsClient sqs = mock(SqsClient.class);
        when(sqs.sendMessage(any(SendMessageRequest.class))).thenThrow(new RuntimeException("SQS unavailable"));
        var publisher = new TaskQueuePublisher(sqs, properties, state, repository, clock);
        publisher.publish(id);
        assertThat(state.get(id).getLastEnqueuedAt()).isNull();
        assertThat(state.get(id).getQueueAttempts()).isZero();
        when(sqs.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("sent").build());
        publisher.recoverPending();
        assertThat(state.get(id).getLastEnqueuedAt()).isEqualTo(NOW);
        verify(sqs, times(2)).sendMessage(any(SendMessageRequest.class));
    }

    @Test void recoveryAlsoFindsExpiredRunningJobsWhoseMessageHasDisappeared() {
        UUID id = create(TaskType.CONTENT_SYNC).getRunId();
        state.markEnqueued(id);
        state.claim(id, true);
        SqsClient sqs = mock(SqsClient.class);
        var publisher = new TaskQueuePublisher(sqs, properties, state, repository, clock);
        publisher.recoverPending();
        verifyNoInteractions(sqs);
        clock.now.set(NOW.plusSeconds(601));
        publisher.recoverPending();
        verify(sqs).sendMessage(any(SendMessageRequest.class));
    }

    @Test void legacyLocalRunsCannotBeClaimedByQueueWorker() {
        var legacy = ((TaskStartResult.Created) runs.start(command(TaskType.CONTENT_SYNC))).run();
        assertThat(legacy.isQueueManaged()).isFalse();
        assertThatThrownBy(() -> state.claim(legacy.getRunId(), true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void neverPublishedCommandsTakePriorityOverPeriodicResends() {
        UUID olderPublished = create(TaskType.KAKAO_MESSAGE_SEND).getRunId();
        state.markEnqueued(olderPublished);
        UUID unpublished = create(TaskType.KAKAO_MESSAGE_SEND).getRunId();
        clock.now.set(NOW.plusSeconds(601));
        assertThat(repository.findQueuePublishCandidates(TaskRunStatus.QUEUED, TaskRunStatus.RUNNING,
                clock.instant(), clock.instant().minusSeconds(600),
                org.springframework.data.domain.PageRequest.of(0, 1))).containsExactly(unpublished);
    }

    private TaskRun create(TaskType type) {
        return ((TaskStartResult.Created) runs.start(command(type), true)).run();
    }

    private TaskStartCommand command(TaskType type) {
        return new TaskStartCommand(type, TriggerType.ADMIN_TRIGGERED, 1L, UUID.randomUUID(),
                mapper.createObjectNode().put("source", "youtube-category").put("categoryId", 7));
    }

    @TestConfiguration
    @EnableConfigurationProperties(TaskRunProperties.class)
    static class Config {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean @Primary MutableClock clock() { return new MutableClock(); }
        @Bean TaskQueueProperties queueProperties() {
            return new TaskQueueProperties(true, true, "https://sqs.test/queue", "https://sqs.test/dlq",
                    "ap-northeast-2", 1, 300, 120, 30, 3, 30, 600);
        }
    }

    static class MutableClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(NOW);
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }
}
