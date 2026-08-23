package com.fuma.hiselectors.taskrun.repository;

import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskRunStatus;
import com.fuma.hiselectors.taskrun.model.TaskType;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRunRepository extends JpaRepository<TaskRun, Long> {

    Optional<TaskRun> findByRunId(UUID runId);

    Optional<TaskRun> findByIdempotencyKey(UUID idempotencyKey);

    Optional<TaskRun> findByConcurrencyKey(String concurrencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from TaskRun run where run.runId = :runId")
    Optional<TaskRun> findByRunIdForUpdate(@Param("runId") UUID runId);

    @Query("""
            select run.runId
            from TaskRun run
            where run.taskType = :taskType
              and run.status in :statuses
              and run.heartbeatAt < :cutoff
            """)
    List<UUID> findStaleCandidateRunIds(
            @Param("taskType") TaskType taskType,
            @Param("statuses") Collection<TaskRunStatus> statuses,
            @Param("cutoff") Instant cutoff);
}
