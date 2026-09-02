package com.fuma.hiselectors.taskrun.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TaskQueueHealthIndicatorTest {
    private final TaskQueueWorker worker = mock(TaskQueueWorker.class);

    @Test
    void readinessReflectsPollingHealthWithoutLeakingQueueDetails() {
        TaskQueueHealthIndicator indicator = new TaskQueueHealthIndicator(worker);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(indicator.health().getDetails()).isEmpty();

        when(worker.isPollingHealthy()).thenReturn(true);
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(indicator.health().getDetails()).isEmpty();

        when(worker.isPollingHealthy()).thenReturn(false);
        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void indicatorExistsOnlyInTheConsumerRole() {
        ApplicationContextRunner context = new ApplicationContextRunner()
                .withUserConfiguration(TaskQueueHealthIndicator.class)
                .withBean(TaskQueueWorker.class, () -> worker);

        context.run(application -> assertThat(application).doesNotHaveBean(TaskQueueHealthIndicator.class));
        context.withPropertyValues("task-queue.worker-enabled=false")
                .run(application -> assertThat(application).doesNotHaveBean(TaskQueueHealthIndicator.class));
        context.withPropertyValues("task-queue.worker-enabled=true")
                .run(application -> assertThat(application).hasSingleBean(TaskQueueHealthIndicator.class));
    }
}
