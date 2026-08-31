package com.fuma.hiselectors.taskrun.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskRunStatus;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import com.fuma.hiselectors.taskrun.queue.TaskQueueState.Claim;
import com.fuma.hiselectors.taskrun.queue.TaskQueueState.Disposition;
import com.fuma.hiselectors.taskrun.service.TaskLease;
import com.fuma.hiselectors.taskrun.service.TaskRunExecutionService;
import com.fuma.hiselectors.taskrun.service.TaskRunProgressStream;
import com.fuma.hiselectors.taskrun.service.TaskRunTaskResolver;
import com.fuma.hiselectors.taskrun.service.TaskRunTerminalSnapshot;
import com.fuma.hiselectors.taskrun.service.TaskTerminalContext;
import com.fuma.hiselectors.taskrun.service.TrackedTask;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

class TaskQueueWorkerContractTest {

    private static final UUID RUN_ID = UUID.fromString("97f0a053-2bde-4932-b8e4-58f3f98236c1");
    private static final String RECEIPT = "delivery-receipt";
    private static final TaskLease LEASE = new TaskLease(RUN_ID, UUID.randomUUID());
    private static final TaskQueueProperties PROPERTIES = new TaskQueueProperties(
            true, true, "https://sqs.example/batch", "https://sqs.example/batch-dlq",
            "ap-northeast-2", 1, 600, 300, 60, 3, 30, 1200);

    private final SqsClient sqs = mock(SqsClient.class);
    private final TaskQueueState state = mock(TaskQueueState.class);
    private final TaskQueuePublisher publisher = mock(TaskQueuePublisher.class);
    private final TaskRunTaskResolver resolver = mock(TaskRunTaskResolver.class);
    private final TaskRunExecutionService execution = mock(TaskRunExecutionService.class);
    private final TaskRunProgressStream progress = mock(TaskRunProgressStream.class);
    private final TaskRun run = mock(TaskRun.class);
    private final TaskRun completed = mock(TaskRun.class);
    private final TrackedTask task = mock(TrackedTask.class);
    private TaskQueueWorker worker;

    @BeforeEach
    void setUp() {
        when(run.getRunId()).thenReturn(RUN_ID);
        when(run.isQueueManaged()).thenReturn(true);
        when(run.getTaskType()).thenReturn(TaskType.CONTENT_SYNC);
        when(run.getQueueAttempts()).thenReturn(1);
        when(completed.getRunId()).thenReturn(RUN_ID);
        when(completed.getStatus()).thenReturn(TaskRunStatus.SUCCEEDED);
        when(state.get(RUN_ID)).thenReturn(run, completed);
        when(resolver.resolve(run)).thenReturn(task);
        when(resolver.automaticRetrySafe(TaskType.CONTENT_SYNC)).thenReturn(true);
        when(state.claim(RUN_ID, true)).thenReturn(new Claim(run, LEASE, Disposition.CLAIMED));
        when(state.finish(LEASE, null, true)).thenReturn(Disposition.SUCCEEDED);
        worker = new TaskQueueWorker(PROPERTIES, sqs, state, publisher, resolver, execution, progress);
    }

    @AfterEach
    void stopWorkerWithoutStartingPolling() throws InterruptedException {
        Thread.interrupted();
        CountDownLatch stopped = new CountDownLatch(1);
        worker.stop(stopped::countDown);
        assertThat(stopped.await(5, TimeUnit.SECONDS)).isTrue();
        verifyNoInteractions(publisher);
    }

    @Test
    void acknowledgesOnlyAfterBusinessWorkAndCommittedCompletion() throws Exception {
        doAnswer(invocation -> {
            verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
            verify(state, never()).finish(eq(LEASE), any(), anyBoolean());
            return null;
        }).when(execution).executeQueued(LEASE, task);
        when(state.finish(LEASE, null, true)).thenAnswer(invocation -> {
            verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
            return Disposition.SUCCEEDED;
        });

        worker.handle(message(RUN_ID.toString()));

        InOrder order = inOrder(execution, state, task, sqs);
        order.verify(execution).executeQueued(LEASE, task);
        order.verify(state).finish(LEASE, null, true);
        order.verify(task).afterTerminal(new TaskTerminalContext(RUN_ID, TaskRunStatus.SUCCEEDED));
        order.verify(sqs).deleteMessage(deleteRequest());
        verify(sqs, never()).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void retainsOriginalIfCompletionCannotBeCommitted() {
        IllegalStateException unavailable = new IllegalStateException("database unavailable");
        when(state.finish(LEASE, null, true)).thenThrow(unavailable);

        assertThatThrownBy(() -> worker.handle(message(RUN_ID.toString()))).isSameAs(unavailable);

        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
        verify(sqs, never()).sendMessage(any(SendMessageRequest.class));
        verify(resolver, never()).afterTerminal(any());
        verify(task, never()).afterTerminal(any());
    }

    @Test
    void sendsFailedBusinessRunToDlqBeforeDeletingOriginal() throws Exception {
        RuntimeException failure = failedBusinessRun();

        worker.handle(message(RUN_ID.toString()));

        InOrder order = inOrder(execution, state, task, sqs);
        order.verify(execution).executeQueued(LEASE, task);
        order.verify(state).finish(LEASE, failure, true);
        order.verify(execution).logFailure(TaskRunTerminalSnapshot.from(completed));
        order.verify(task).afterTerminal(new TaskTerminalContext(RUN_ID, TaskRunStatus.FAILED));
        order.verify(sqs).sendMessage(dlqRequest(RUN_ID.toString()));
        order.verify(sqs).deleteMessage(deleteRequest());
    }

    @Test
    void retainsOriginalIfDlqPublicationFails() throws Exception {
        failedBusinessRun();
        IllegalStateException unavailable = new IllegalStateException("DLQ unavailable");
        when(sqs.sendMessage(any(SendMessageRequest.class))).thenThrow(unavailable);

        assertThatThrownBy(() -> worker.handle(message(RUN_ID.toString()))).isSameAs(unavailable);

        verify(sqs).sendMessage(dlqRequest(RUN_ID.toString()));
        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @ParameterizedTest
    @EnumSource(value = Disposition.class, names = {"SUCCEEDED", "FAILED"})
    void duplicateTerminalDeliveryNeverExecutesBusinessAgain(Disposition result) {
        TaskRunStatus status = result == Disposition.SUCCEEDED
                ? TaskRunStatus.SUCCEEDED : TaskRunStatus.FAILED;
        when(run.isTerminal()).thenReturn(true);
        when(run.getStatus()).thenReturn(status);

        worker.handle(message(RUN_ID.toString()));

        verifyNoInteractions(execution);
        verify(resolver, never()).resolve(any());
        verify(state, never()).claim(any(), anyBoolean());
        verify(state, never()).finish(any(), any(), anyBoolean());
        verify(resolver).afterTerminal(run);
        if (result == Disposition.FAILED) {
            verify(sqs).sendMessage(dlqRequest(RUN_ID.toString()));
        } else {
            verify(sqs, never()).sendMessage(any(SendMessageRequest.class));
        }
        verify(sqs).deleteMessage(deleteRequest());
    }

    @Test
    void busyLeaseOnlyDefersThisDelivery() {
        when(state.claim(RUN_ID, true)).thenReturn(new Claim(run, null, Disposition.BUSY));

        worker.handle(message(RUN_ID.toString()));

        verify(sqs).changeMessageVisibility(visibilityRequest(PROPERTIES.leaseSeconds()));
        verifyNoInteractions(execution, task);
        verify(state, never()).finish(any(), any(), anyBoolean());
        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
        verify(sqs, never()).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void runCompletedBetweenReadAndClaimIsNotExecutedAgain() {
        when(state.claim(RUN_ID, true)).thenReturn(new Claim(completed, null, Disposition.SUCCEEDED));

        worker.handle(message(RUN_ID.toString()));

        verifyNoInteractions(execution);
        verify(state, never()).finish(any(), any(), anyBoolean());
        verify(task).afterTerminal(new TaskTerminalContext(RUN_ID, TaskRunStatus.SUCCEEDED));
        verify(sqs).deleteMessage(deleteRequest());
    }

    @Test
    void retryDispositionDefersWithoutTerminalHookOrAcknowledgement() throws Exception {
        RuntimeException failure = new RuntimeException("temporary upstream failure");
        doThrow(failure).when(execution).executeQueued(LEASE, task);
        when(state.finish(LEASE, failure, true)).thenReturn(Disposition.RETRY);

        worker.handle(message(RUN_ID.toString()));

        verify(state).finish(LEASE, failure, true);
        verify(sqs).changeMessageVisibility(visibilityRequest(PROPERTIES.retryDelaySeconds()));
        verify(resolver, never()).afterTerminal(any());
        verify(task, never()).afterTerminal(any());
        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
        verify(sqs, never()).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void interruptedAttemptKeepsMessageAndDoesNotReportCompletion() throws Exception {
        doThrow(new InterruptedException("worker shutting down"))
                .when(execution).executeQueued(LEASE, task);

        worker.handle(message(RUN_ID.toString()));

        verify(state, never()).finish(any(), any(), anyBoolean());
        verify(resolver, never()).afterTerminal(any());
        verify(task, never()).afterTerminal(any());
        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
        verify(sqs, never()).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void unsafeBusinessTypeIsNotUpgradedToAutomaticRetry() throws Exception {
        when(run.getTaskType()).thenReturn(TaskType.PROPOSAL_EMAIL_SEND);
        when(state.claim(RUN_ID, false)).thenReturn(new Claim(run, LEASE, Disposition.CLAIMED));
        when(state.finish(LEASE, null, false)).thenReturn(Disposition.SUCCEEDED);

        worker.handle(message(RUN_ID.toString()));

        verify(state).claim(RUN_ID, false);
        verify(state).finish(LEASE, null, false);
        verify(sqs).deleteMessage(deleteRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Unsupported scope", "Initiating admin no longer exists"})
    void permanentCommandErrorsAreRejectedWithoutClaimOrFallbackExecution(String reason) {
        when(resolver.resolve(run)).thenThrow(new IllegalArgumentException(reason));

        worker.handle(message(RUN_ID.toString()));

        InOrder order = inOrder(state, sqs);
        order.verify(state).reject(RUN_ID);
        order.verify(sqs).sendMessage(dlqRequest(RUN_ID.toString()));
        order.verify(sqs).deleteMessage(deleteRequest());
        verify(state, never()).claim(any(), anyBoolean());
        verifyNoInteractions(execution, task);
    }

    @Test
    void queueCannotReplayALegacyLocalExecution() {
        when(run.isQueueManaged()).thenReturn(false);

        worker.handle(message(RUN_ID.toString()));

        verify(state).reject(RUN_ID);
        verify(state, never()).claim(any(), anyBoolean());
        verifyNoInteractions(resolver, execution, task);
        verify(sqs).sendMessage(dlqRequest(RUN_ID.toString()));
        verify(sqs).deleteMessage(deleteRequest());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"not-a-run-id", "1-1-1-1-1", "{\"source\":\"youtube\"}",
            "97F0A053-2BDE-4932-B8E4-58F3F98236C1"})
    void invalidMessageBodyCannotChooseOrBroadenAJob(String body) {
        worker.handle(message(body));

        InOrder order = inOrder(sqs);
        order.verify(sqs).sendMessage(dlqRequest(body == null ? "invalid-empty-message" : body));
        order.verify(sqs).deleteMessage(deleteRequest());
        verifyNoInteractions(state, resolver, execution, task);
    }

    @Test
    void missingRunIsDeadLetteredWithoutExecution() {
        when(state.get(RUN_ID)).thenThrow(new IllegalArgumentException("Unknown queued run"));
        doThrow(new IllegalArgumentException("Unknown queued run")).when(state).reject(RUN_ID);

        worker.handle(message(RUN_ID.toString()));

        verify(sqs).sendMessage(dlqRequest(RUN_ID.toString()));
        verify(sqs).deleteMessage(deleteRequest());
        verifyNoInteractions(resolver, execution, task);
    }

    @Test
    void databaseOutageBeforeClaimIsNotMisclassifiedAsPoison() {
        IllegalStateException unavailable = new IllegalStateException("database unavailable");
        when(state.get(RUN_ID)).thenThrow(unavailable);

        assertThatThrownBy(() -> worker.handle(message(RUN_ID.toString()))).isSameAs(unavailable);

        verify(state, never()).reject(any());
        verify(state, never()).claim(any(), anyBoolean());
        verifyNoInteractions(sqs, resolver, execution, task);
    }

    private RuntimeException failedBusinessRun() throws Exception {
        RuntimeException failure = new RuntimeException("business failed");
        doThrow(failure).when(execution).executeQueued(LEASE, task);
        when(state.finish(LEASE, failure, true)).thenReturn(Disposition.FAILED);
        when(completed.getStatus()).thenReturn(TaskRunStatus.FAILED);
        when(completed.getTaskType()).thenReturn(TaskType.CONTENT_SYNC);
        when(completed.getTriggerType()).thenReturn(TriggerType.ADMIN_TRIGGERED);
        when(completed.getFinishedAt()).thenReturn(Instant.parse("2026-08-31T12:00:00Z"));
        return failure;
    }

    private Message message(String body) {
        return Message.builder().body(body).messageId("message-id").receiptHandle(RECEIPT).build();
    }

    private DeleteMessageRequest deleteRequest() {
        return DeleteMessageRequest.builder().queueUrl(PROPERTIES.url()).receiptHandle(RECEIPT).build();
    }

    private SendMessageRequest dlqRequest(String body) {
        return SendMessageRequest.builder().queueUrl(PROPERTIES.dlqUrl()).messageBody(body).build();
    }

    private ChangeMessageVisibilityRequest visibilityRequest(int seconds) {
        return ChangeMessageVisibilityRequest.builder().queueUrl(PROPERTIES.url())
                .receiptHandle(RECEIPT).visibilityTimeout(seconds).build();
    }
}
