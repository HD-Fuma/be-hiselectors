package com.fuma.hiselectors.taskrun.queue;

import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskRunStatus;
import com.fuma.hiselectors.taskrun.queue.TaskQueueState.Disposition;
import com.fuma.hiselectors.taskrun.service.TaskRunExecutionService;
import com.fuma.hiselectors.taskrun.service.TaskRunProgressStream;
import com.fuma.hiselectors.taskrun.service.TaskRunTaskResolver;
import com.fuma.hiselectors.taskrun.service.TaskRunTerminalSnapshot;
import com.fuma.hiselectors.taskrun.service.TaskTerminalContext;
import com.fuma.hiselectors.taskrun.service.TrackedTask;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/** Long-poll only when a processing slot is free; never ACK merely for starting a task. */
@Component
@Slf4j
@ConditionalOnProperty(name = "task-queue.worker-enabled", havingValue = "true")
public class TaskQueueWorker implements SmartLifecycle {
    private final TaskQueueProperties properties;
    private final SqsClient sqs;
    private final TaskQueueState state;
    private final TaskQueuePublisher publisher;
    private final TaskRunTaskResolver resolver;
    private final TaskRunExecutionService execution;
    private final TaskRunProgressStream progress;
    private final ExecutorService consumers;
    private final ScheduledExecutorService timers;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong lastHealthyActivity = new AtomicLong();

    public TaskQueueWorker(TaskQueueProperties properties,
            @Qualifier("taskQueueSqsClient") SqsClient sqs, TaskQueueState state,
            TaskQueuePublisher publisher, TaskRunTaskResolver resolver,
            TaskRunExecutionService execution, TaskRunProgressStream progress) {
        this.properties = properties;
        this.sqs = sqs;
        this.state = state;
        this.publisher = publisher;
        this.resolver = resolver;
        this.execution = execution;
        this.progress = progress;
        this.consumers = Executors.newFixedThreadPool(properties.concurrency(),
                Thread.ofPlatform().daemon().name("task-queue-consumer-", 0).factory());
        this.timers = Executors.newScheduledThreadPool(2,
                Thread.ofPlatform().daemon().name("task-queue-maintenance-", 0).factory());
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        timers.scheduleWithFixedDelay(this::recoverSafely, 0, 60, TimeUnit.SECONDS);
        for (int index = 0; index < properties.concurrency(); index++) {
            consumers.submit(this::poll);
        }
    }

    private void poll() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                var messages = sqs.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(properties.url()).maxNumberOfMessages(1).waitTimeSeconds(20)
                        .visibilityTimeout(properties.visibilitySeconds()).build()).messages();
                lastHealthyActivity.set(System.nanoTime());
                for (Message message : messages) {
                    if (running.get()) {
                        handle(message);
                        if (running.get()) {
                            // An aborted lease must not silently retire a healthy consumer slot.
                            Thread.interrupted();
                        }
                    }
                }
            } catch (RuntimeException failure) {
                log.warn("Queue consumer deferred: errorType={}", failure.getClass().getSimpleName());
                try {
                    Thread.sleep(2_000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    void handle(Message message) {
        UUID runId;
        try {
            runId = UUID.fromString(message.body());
            if (!runId.toString().equals(message.body())) {
                throw new IllegalArgumentException("Noncanonical run ID");
            }
        } catch (IllegalArgumentException | NullPointerException invalid) {
            deadLetter(message);
            return;
        }

        TaskRun run;
        TrackedTask task;
        try {
            run = state.get(runId);
            if (!run.isQueueManaged()) {
                throw new IllegalArgumentException("Not a queued command");
            }
            if (run.isTerminal()) {
                resolver.afterTerminal(run);
                if (run.getStatus() == TaskRunStatus.SUCCEEDED) {
                    acknowledge(message);
                } else {
                    deadLetter(message);
                }
                return;
            }
            task = resolver.resolve(run);
        } catch (IllegalArgumentException invalid) {
            try {
                state.reject(runId);
            } catch (IllegalArgumentException unknownRun) {
                // An unknown ID or command is poison, not a transient database error.
            }
            log.warn("Queue command rejected: runId={}", runId);
            deadLetter(message);
            return;
        }

        boolean retrySafe = resolver.automaticRetrySafe(run.getTaskType());
        var claim = state.claim(runId, retrySafe);
        if (claim.disposition() == Disposition.BUSY) {
            defer(message, properties.leaseSeconds());
            return;
        }
        if (claim.disposition() != Disposition.CLAIMED) {
            finishMessage(message, runId, task, claim.disposition());
            return;
        }

        progress.publishChanged(runId);
        AtomicBoolean leaseLost = new AtomicBoolean();
        AtomicBoolean working = new AtomicBoolean(true);
        Object leaseGuard = new Object();
        Thread owner = Thread.currentThread();
        var heartbeat = timers.scheduleWithFixedDelay(() -> {
            synchronized (leaseGuard) {
                if (!working.get()) {
                    return;
                }
                try {
                    state.heartbeat(claim.lease());
                    defer(message, properties.visibilitySeconds());
                    lastHealthyActivity.set(System.nanoTime());
                } catch (RuntimeException failure) {
                    leaseLost.set(true);
                    owner.interrupt();
                    log.warn("Queue lease refresh failed: runId={}, errorType={}",
                            runId, failure.getClass().getSimpleName());
                }
            }
        }, properties.heartbeatSeconds(), properties.heartbeatSeconds(), TimeUnit.SECONDS);
        try {
            Exception failure = null;
            try {
                execution.executeQueued(claim.lease(), task);
            } catch (Exception exception) {
                failure = exception;
                log.warn("Queue business attempt failed: runId={}, attempt={}, errorType={}",
                        runId, claim.run().getQueueAttempts(), exception.getClass().getSimpleName());
            }
            synchronized (leaseGuard) {
                working.set(false);
                heartbeat.cancel(false);
                if (leaseLost.get() || Thread.currentThread().isInterrupted()
                        || failure instanceof InterruptedException) {
                    // Keep the message and DB lease until expiry; do not report a false success.
                    return;
                }
            }
            Disposition result = state.finish(claim.lease(), failure, retrySafe);
            heartbeat.cancel(false);
            progress.publishChanged(runId);
            if (result == Disposition.RETRY) {
                defer(message, properties.retryDelaySeconds());
            } else {
                finishMessage(message, runId, task, result);
            }
        } finally {
            working.set(false);
            heartbeat.cancel(false);
        }
    }

    private void finishMessage(Message message, UUID runId, TrackedTask task, Disposition result) {
        TaskRun completed = state.get(runId);
        if (completed.getStatus() != TaskRunStatus.SUCCEEDED) {
            execution.logFailure(TaskRunTerminalSnapshot.from(completed));
        }
        // Existing content-report follow-up uses an idempotency key derived from the parent runId.
        task.afterTerminal(new TaskTerminalContext(runId, completed.getStatus()));
        if (result == Disposition.SUCCEEDED) {
            acknowledge(message);
        } else {
            deadLetter(message);
        }
    }

    private void deadLetter(Message message) {
        // Send first. If it fails, retain the source message; SQS redrive is the final safety net.
        sqs.sendMessage(SendMessageRequest.builder().queueUrl(properties.dlqUrl())
                .messageBody(message.body() == null ? "invalid-empty-message" : message.body()).build());
        acknowledge(message);
    }

    private void acknowledge(Message message) {
        sqs.deleteMessage(DeleteMessageRequest.builder().queueUrl(properties.url())
                .receiptHandle(message.receiptHandle()).build());
    }

    private void defer(Message message, int seconds) {
        sqs.changeMessageVisibility(ChangeMessageVisibilityRequest.builder().queueUrl(properties.url())
                .receiptHandle(message.receiptHandle()).visibilityTimeout(seconds).build());
    }

    private void recoverSafely() {
        try {
            publisher.recoverPending();
        } catch (RuntimeException failure) {
            log.warn("Queue recovery deferred: errorType={}", failure.getClass().getSimpleName());
        }
    }

    @Override
    public void stop(Runnable callback) {
        running.set(false);
        consumers.shutdown();
        Thread.ofVirtual().name("task-queue-drain").start(() -> {
            try {
                consumers.awaitTermination(85, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                consumers.shutdownNow();
                timers.shutdownNow();
                callback.run();
            }
        });
    }

    @Override public void stop() { stop(() -> { }); }
    @Override public boolean isRunning() { return running.get(); }
    public boolean isPollingHealthy() {
        long lastActivity = lastHealthyActivity.get();
        return running.get() && lastActivity != 0
                && System.nanoTime() - lastActivity < TimeUnit.SECONDS.toNanos(90);
    }
    @Override public int getPhase() { return Integer.MAX_VALUE - 100; }
}
