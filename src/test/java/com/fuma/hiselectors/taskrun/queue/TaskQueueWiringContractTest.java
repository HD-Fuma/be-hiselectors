package com.fuma.hiselectors.taskrun.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.taskrun.config.TaskRunProperties;
import com.fuma.hiselectors.taskrun.logging.TaskRunFailureLogger;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import com.fuma.hiselectors.taskrun.repository.TaskRunRepository;
import com.fuma.hiselectors.taskrun.service.TaskLeaseTransaction;
import com.fuma.hiselectors.taskrun.service.TaskRunExecutionService;
import com.fuma.hiselectors.taskrun.service.TaskRunProgressStream;
import com.fuma.hiselectors.taskrun.service.TaskRunService;
import com.fuma.hiselectors.taskrun.service.TaskStartCommand;
import com.fuma.hiselectors.taskrun.service.TaskStartResult;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.databind.ObjectMapper;

class TaskQueueWiringContractTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void springFeatureFlagSelectsExactlyOneExecutionPath(boolean enabled) {
        TaskRunService service = mock(TaskRunService.class);
        TaskExecutor executor = mock(TaskExecutor.class);
        SqsClient sqs = mock(SqsClient.class);
        TaskQueueState state = mock(TaskQueueState.class);
        TaskRun run = mock(TaskRun.class);
        when(run.getRunId()).thenReturn(UUID.randomUUID());
        TaskStartCommand command = new TaskStartCommand(TaskType.CREATOR_SYNC,
                TriggerType.ADMIN_TRIGGERED, 1L, UUID.randomUUID(),
                new ObjectMapper().valueToTree(Map.of("source", "youtube")));
        TaskStartResult.Created created = new TaskStartResult.Created(run);
        when(service.start(command)).thenReturn(created);
        when(service.start(command, true)).thenReturn(created);

        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfiguration.class,
                        TaskQueuePublisher.class, TaskRunExecutionService.class)
                .withPropertyValues("task-queue.enabled=" + enabled,
                        "task-queue.url=https://sqs.example/batch", "task-queue.region=ap-northeast-2",
                        "task-queue.concurrency=1", "task-queue.visibility-seconds=300",
                        "task-queue.lease-seconds=120", "task-queue.heartbeat-seconds=30",
                        "task-queue.max-attempts=3", "task-queue.retry-delay-seconds=30",
                        "task-queue.resend-after-seconds=600")
                .withBean(TaskRunService.class, () -> service)
                .withBean("taskRunExecutor", TaskExecutor.class, () -> executor)
                .withBean("taskQueueSqsClient", SqsClient.class, () -> sqs)
                .withBean(TaskQueueState.class, () -> state)
                .withBean(TaskRunRepository.class, () -> mock(TaskRunRepository.class))
                .withBean(TaskLeaseTransaction.class, () -> mock(TaskLeaseTransaction.class))
                .withBean(TaskRunProperties.class, () -> mock(TaskRunProperties.class))
                .withBean(TaskRunFailureLogger.class, () -> mock(TaskRunFailureLogger.class))
                .withBean(TaskRunProgressStream.class, () -> mock(TaskRunProgressStream.class))
                .withBean(Clock.class, Clock::systemUTC)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(TaskRunExecutionService.class)
                            .submit(command, ignored -> { })).isSameAs(created);
                    if (enabled) {
                        verify(service).start(command, true);
                        verify(service, never()).start(command);
                        verifyNoInteractions(executor);
                        verify(sqs).sendMessage(any(SendMessageRequest.class));
                        verify(state).markEnqueued(run.getRunId());
                    } else {
                        verify(service).start(command);
                        verify(service, never()).start(command, true);
                        verify(executor).execute(any(Runnable.class));
                        verifyNoInteractions(sqs, state);
                    }
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TaskQueueProperties.class)
    static class PropertiesConfiguration { }
}
