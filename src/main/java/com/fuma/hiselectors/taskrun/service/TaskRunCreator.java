package com.fuma.hiselectors.taskrun.service;

import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.repository.TaskRunRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskRunCreator {

    private final TaskRunRepository repository;
    private final Clock clock;

    public TaskRunCreator(TaskRunRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TaskRun create(TaskStartCommand command, String fingerprint, String concurrencyKey) {
        return repository.saveAndFlush(TaskRun.queued(
                command.taskType(),
                command.triggerType(),
                command.startedByAdminId(),
                command.idempotencyKey(),
                fingerprint,
                concurrencyKey,
                clock.instant()));
    }
}
