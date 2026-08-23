package com.fuma.hiselectors.taskrun.repository;

import com.fuma.hiselectors.taskrun.model.TaskRun;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRunRepository extends JpaRepository<TaskRun, Long> {

    Optional<TaskRun> findByRunId(UUID runId);

    Optional<TaskRun> findByIdempotencyKey(UUID idempotencyKey);

    Optional<TaskRun> findByConcurrencyKey(String concurrencyKey);
}
