package com.fuma.hiselectors.taskrun.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fuma.hiselectors.config.CacheConfig;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.annotation.Import;

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

    private TaskRun queued(UUID idempotencyKey, String concurrencyKey) {
        return TaskRun.queued(
                TaskType.CONTENT_SYNC,
                TriggerType.SCHEDULED,
                null,
                idempotencyKey,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                concurrencyKey,
                NOW);
    }
}
