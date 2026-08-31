package com.fuma.hiselectors.taskrun.queue;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Read cached worker activity only; readiness probes must not issue extra queue requests. */
@Component
@ConditionalOnProperty(name = "task-queue.worker-enabled", havingValue = "true")
public class TaskQueueHealthIndicator implements HealthIndicator {
    private final TaskQueueWorker worker;

    public TaskQueueHealthIndicator(TaskQueueWorker worker) {
        this.worker = worker;
    }

    @Override
    public Health health() {
        return (worker.isPollingHealthy() ? Health.up() : Health.down()).build();
    }
}
