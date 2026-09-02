package com.fuma.hiselectors.taskrun.queue;

import com.fuma.hiselectors.taskrun.model.TaskRunStatus;
import com.fuma.hiselectors.taskrun.repository.TaskRunRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Component
@Slf4j
@ConditionalOnProperty(name = "task-queue.enabled", havingValue = "true")
public class TaskQueuePublisher {
    private final SqsClient sqs;
    private final TaskQueueProperties properties;
    private final TaskQueueState state;
    private final TaskRunRepository repository;
    private final Clock clock;

    public TaskQueuePublisher(@Qualifier("taskQueueSqsClient") SqsClient sqs,
            TaskQueueProperties properties, TaskQueueState state,
            TaskRunRepository repository, Clock clock) {
        this.sqs = sqs;
        this.properties = properties;
        this.state = state;
        this.repository = repository;
        this.clock = clock;
    }

    /** DB commit precedes publication; failure leaves a durable command for the recovery scan. */
    public void publish(UUID runId) {
        try {
            sqs.sendMessage(SendMessageRequest.builder().queueUrl(properties.url())
                    .messageBody(runId.toString())
                    .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(2))
                            .apiCallAttemptTimeout(Duration.ofSeconds(1)))
                    .build());
            state.markEnqueued(runId);
        } catch (RuntimeException failure) {
            log.warn("Queue publication deferred: runId={}, errorType={}",
                    runId, failure.getClass().getSimpleName());
        }
    }

    public void recoverPending() {
        Instant now = clock.instant();
        // A duplicate runId is harmless; rescan also covers lost sends and retention expiry.
        for (UUID runId : repository.findQueuePublishCandidates(TaskRunStatus.QUEUED, TaskRunStatus.RUNNING,
                now, now.minusSeconds(properties.resendAfterSeconds()), PageRequest.of(0, 20))) {
            publish(runId);
        }
    }
}
