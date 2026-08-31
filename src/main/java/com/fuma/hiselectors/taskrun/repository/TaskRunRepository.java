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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRunRepository extends JpaRepository<TaskRun, Long> {

    Optional<TaskRun> findByRunId(UUID runId);

    Optional<TaskRun> findByIdempotencyKey(UUID idempotencyKey);

    Optional<TaskRun> findByConcurrencyKey(String concurrencyKey);

    @Query("""
            select run
            from TaskRun run
            where run.status in :statuses
            order by coalesce(run.startedAt, run.heartbeatAt), run.id
            """)
    List<TaskRun> findActiveRuns(@Param("statuses") Collection<TaskRunStatus> statuses);

    @Query(value = """
            select run
            from TaskRun run
            where run.status in :statuses
            order by run.finishedAt desc, run.id desc
            """, countQuery = """
            select count(run)
            from TaskRun run
            where run.status in :statuses
            """)
    Page<TaskRun> findTerminalRuns(
            @Param("statuses") Collection<TaskRunStatus> statuses,
            Pageable pageable);

    @Query("""
            select run
            from TaskRun run
            where run.status in :statuses
              and run.finishedAt >= :cutoff
            order by run.finishedAt desc, run.id desc
            """)
    List<TaskRun> findRecentTerminalRuns(
            @Param("statuses") Collection<TaskRunStatus> statuses,
            @Param("cutoff") Instant cutoff,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from TaskRun run where run.runId = :runId")
    Optional<TaskRun> findByRunIdForUpdate(@Param("runId") UUID runId);

    @Query("""
            select run.runId from TaskRun run
            where run.queueManaged = true
              and ((run.status = :queued and run.queueAvailableAt <= :now)
                or (run.status = :running and run.queueLeaseUntil <= :now))
              and (run.lastEnqueuedAt is null or run.lastEnqueuedAt < :resendBefore)
            order by case when run.lastEnqueuedAt is null then 0 else 1 end,
                     run.lastEnqueuedAt, run.id
            """)
    List<UUID> findQueuePublishCandidates(@Param("queued") TaskRunStatus queued,
            @Param("running") TaskRunStatus running,
            @Param("now") Instant now, @Param("resendBefore") Instant resendBefore,
            Pageable pageable);

    @Query("""
            select run.runId as runId, run.version as version from TaskRun run
            where run.queueManaged = true and run.heartbeatAt >= :since
            order by run.heartbeatAt, run.id
            """)
    List<QueueChange> findQueueChangesSince(@Param("since") Instant since, Pageable pageable);

    interface QueueChange {
        UUID getRunId();
        Long getVersion();
    }

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
