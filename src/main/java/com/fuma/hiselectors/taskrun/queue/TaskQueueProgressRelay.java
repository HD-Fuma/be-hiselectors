package com.fuma.hiselectors.taskrun.queue;

import com.fuma.hiselectors.taskrun.repository.TaskRunRepository;
import com.fuma.hiselectors.taskrun.service.TaskRunProgressStream;
import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** Relay durable worker updates to the existing API SSE contract without a second message bus. */
@Component
@Slf4j
@ConditionalOnProperty(name = "task-queue.enabled", havingValue = "true")
public class TaskQueueProgressRelay implements SmartLifecycle {
    private final TaskRunRepository repository;
    private final TaskRunProgressStream stream;
    private final TaskQueueProperties properties;
    private final Clock clock;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("task-queue-progress-relay").factory());
    private volatile boolean running;
    private final Map<UUID, Long> seenVersions = new HashMap<>();

    public TaskQueueProgressRelay(TaskRunRepository repository, TaskRunProgressStream stream,
            TaskQueueProperties properties, Clock clock) {
        this.repository = repository;
        this.stream = stream;
        this.properties = properties;
        this.clock = clock;
    }

    @Override public boolean isAutoStartup() { return !properties.workerEnabled(); }

    @Override
    public void start() {
        if (!running) {
            running = true;
            executor.scheduleWithFixedDelay(this::relay, 3, 3, TimeUnit.SECONDS);
        }
    }

    void relay() {
        if (!stream.hasSubscribers()) {
            return;
        }
        try {
            Set<UUID> recent = new HashSet<>();
            for (var change : repository.findQueueChangesSince(
                    clock.instant().minusSeconds(30), PageRequest.of(0, 500))) {
                recent.add(change.getRunId());
                Long previous = seenVersions.put(change.getRunId(), change.getVersion());
                if (!change.getVersion().equals(previous)) {
                    stream.publishChanged(change.getRunId());
                }
            }
            seenVersions.keySet().retainAll(recent);
        } catch (RuntimeException failure) {
            log.warn("Queue progress relay deferred: errorType={}", failure.getClass().getSimpleName());
        }
    }

    @Override public void stop() { running = false; executor.shutdownNow(); }
    @Override public boolean isRunning() { return running; }
}
