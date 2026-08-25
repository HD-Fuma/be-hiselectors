package com.fuma.hiselectors.taskrun.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fuma.hiselectors.config.CacheConfig;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskRunStatus;
import com.fuma.hiselectors.taskrun.model.TaskStepProgress;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(CacheConfig.class)
class TaskRunRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-08-23T03:00:00Z");

    @Autowired
    private TaskRunRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void persistsUuidIdentifiersAsStringsAndFindsByPublicIdentifiers() {
        UUID idempotencyKey = UUID.randomUUID();
        TaskRun saved = repository.saveAndFlush(queued(idempotencyKey, "content-sync"));
        entityManager.clear();

        assertThat(repository.findByRunId(saved.getRunId()))
                .get().extracting(TaskRun::getRunId).isEqualTo(saved.getRunId());
        assertThat(repository.findByIdempotencyKey(idempotencyKey))
                .get().extracting(TaskRun::getIdempotencyKey).isEqualTo(idempotencyKey);
        assertThat(repository.findByConcurrencyKey("content-sync"))
                .get().extracting(TaskRun::getConcurrencyKey).isEqualTo("content-sync");
        assertThat(entityManager.getEntityManager()
                .createNativeQuery("select run_id from task_run where task_run_id = :id")
                .setParameter("id", saved.getId())
                .getSingleResult()).isEqualTo(saved.getRunId().toString());
    }

    @Test
    void idempotencyKeyIsUnique() {
        UUID duplicateKey = UUID.randomUUID();
        repository.saveAndFlush(queued(duplicateKey, "content-sync-1"));

        assertThatThrownBy(() -> repository.saveAndFlush(
                queued(duplicateKey, "content-sync-2")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nonNullConcurrencyKeyIsUnique() {
        repository.saveAndFlush(queued(UUID.randomUUID(), "content-sync"));

        assertThatThrownBy(() -> repository.saveAndFlush(
                queued(UUID.randomUUID(), "content-sync")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void multipleNullConcurrencyKeysAreAllowed() {
        repository.saveAndFlush(queued(UUID.randomUUID(), null));
        repository.saveAndFlush(queued(UUID.randomUUID(), null));

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void persistsStepProgressAsJsonAndRestoresTypedValues() {
        TaskRun run = running(NOW);
        run.mergeStepProgress(
                Map.of("youtube", new TaskStepProgress(10L, 4L)), NOW.plusSeconds(1));
        TaskRun saved = repository.saveAndFlush(run);
        entityManager.clear();

        assertThat(repository.findById(saved.getId())).get()
                .extracting(TaskRun::getStepProgress)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("youtube", new TaskStepProgress(10L, 4L));
    }

    @Test
    void activeRunsUseHeartbeatForQueuedRunsAndBreakEqualTimesByIdAscending() {
        Instant early = NOW.minusSeconds(20);
        Instant same = NOW.minusSeconds(10);
        Instant late = NOW;
        TaskRun queuedEarly = repository.save(queued(UUID.randomUUID(), null, early));
        TaskRun runningSame = repository.save(running(same));
        TaskRun queuedSame = repository.save(queued(UUID.randomUUID(), null, same));
        TaskRun runningLate = repository.save(running(late));
        repository.flush();
        entityManager.clear();

        assertThat(repository.findActiveRuns(
                List.of(TaskRunStatus.QUEUED, TaskRunStatus.RUNNING)))
                .extracting(TaskRun::getRunId)
                .containsExactly(
                        queuedEarly.getRunId(),
                        runningSame.getRunId(),
                        queuedSame.getRunId(),
                        runningLate.getRunId());
    }

    @Test
    void terminalRunsBreakEqualFinishTimesByIdDescending() {
        TaskRun older = repository.save(terminal(NOW.minusSeconds(1)));
        TaskRun firstAtSameTime = repository.save(terminal(NOW));
        TaskRun secondAtSameTime = repository.save(terminal(NOW));
        repository.flush();
        entityManager.clear();
        List<TaskRunStatus> statuses = List.of(TaskRunStatus.SUCCEEDED);

        assertThat(repository.findTerminalRuns(statuses, PageRequest.of(0, 20)).getContent())
                .extracting(TaskRun::getRunId)
                .containsExactly(
                        secondAtSameTime.getRunId(),
                        firstAtSameTime.getRunId(),
                        older.getRunId());
        assertThat(repository.findRecentTerminalRuns(
                statuses, NOW.minusSeconds(60), PageRequest.of(0, 20)))
                .extracting(TaskRun::getRunId)
                .containsExactly(
                        secondAtSameTime.getRunId(),
                        firstAtSameTime.getRunId(),
                        older.getRunId());
    }

    private TaskRun queued(UUID idempotencyKey, String concurrencyKey) {
        return queued(idempotencyKey, concurrencyKey, NOW);
    }

    private TaskRun queued(UUID idempotencyKey, String concurrencyKey, Instant now) {
        return TaskRun.queued(
                TaskType.CONTENT_SYNC,
                TriggerType.SCHEDULED,
                null,
                idempotencyKey,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                concurrencyKey,
                now);
    }

    private TaskRun running(Instant startedAt) {
        TaskRun run = queued(UUID.randomUUID(), null, startedAt.minusSeconds(1));
        run.markRunning(UUID.randomUUID(), startedAt);
        return run;
    }

    private TaskRun terminal(Instant finishedAt) {
        TaskRun run = running(finishedAt.minusSeconds(1));
        run.complete(finishedAt);
        return run;
    }
}
