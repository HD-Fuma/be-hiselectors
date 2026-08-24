package com.fuma.hiselectors.taskrun.service;

import static com.fuma.hiselectors.exception.ErrorCode.TASK_RUN_LEASE_LOST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fuma.hiselectors.config.CacheConfig;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import com.fuma.hiselectors.taskrun.repository.TaskRunRepository;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({
        CacheConfig.class,
        TaskLeaseTransaction.class,
        TaskLeaseTransactionTest.FixedClockConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TaskLeaseTransactionTest {

    private static final Instant STARTED_AT = Instant.parse("2026-08-23T02:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-23T03:00:00Z");

    @Autowired
    private TaskRunRepository repository;

    @Autowired
    private TaskLeaseTransaction transaction;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearRuns() {
        repository.deleteAll();
        jdbc.execute("""
                create table if not exists task_lease_probe (
                    probe_id bigint primary key,
                    probe_value varchar(100)
                )
                """);
        jdbc.update("delete from task_lease_probe");
    }

    @Test
    void appliesCountsAndHeartbeatAtomicallyForTheCurrentRunningLease() {
        TaskLease lease = runningRun();

        transaction.apply(lease, "STORE", 4, true, "크리에이터 4명 수집", 2, 1, 1, UPDATED_AT);

        TaskRun updated = repository.findByRunId(lease.runId()).orElseThrow();
        assertThat(updated.getCurrentStep()).isEqualTo("STORE");
        assertThat(updated.getTotalCount()).isEqualTo(4);
        assertThat(updated.getSucceededCount()).isEqualTo(2);
        assertThat(updated.getFailedCount()).isEqualTo(1);
        assertThat(updated.getSkippedCount()).isEqualTo(1);
        assertThat(updated.getProcessedCount()).isEqualTo(4);
        assertThat(updated.getProgressMessage()).isEqualTo("크리에이터 4명 수집");
        assertThat(updated.getHeartbeatAt()).isEqualTo(UPDATED_AT);
    }

    @Test
    void rejectsAWorkerThatDoesNotHoldTheCurrentLease() {
        TaskLease lease = runningRun();

        assertThatThrownBy(() -> transaction.apply(
                new TaskLease(lease.runId(), UUID.randomUUID()),
                null,
                null,
                false,
                null,
                1,
                0,
                0,
                UPDATED_AT))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(TASK_RUN_LEASE_LOST));

        assertThat(repository.findByRunId(lease.runId())).get()
                .extracting(TaskRun::getProcessedCount).isEqualTo(0L);
    }

    @Test
    void commitsDomainWriteCountsAndHeartbeatTogether() {
        TaskLease lease = runningRun();

        transaction.execute(
                lease,
                2,
                1,
                0,
                () -> jdbc.update(
                        "insert into task_lease_probe (probe_id, probe_value) values (1, 'saved')"));

        assertThat(jdbc.queryForObject("select count(*) from task_lease_probe", Long.class)).isEqualTo(1L);
        TaskRun updated = repository.findByRunId(lease.runId()).orElseThrow();
        assertThat(updated.getProcessedCount()).isEqualTo(3);
        assertThat(updated.getSucceededCount()).isEqualTo(2);
        assertThat(updated.getFailedCount()).isEqualTo(1);
        assertThat(updated.getHeartbeatAt()).isEqualTo(UPDATED_AT);
    }

    @Test
    void rollsBackDomainWriteAndProgressWhenWorkFails() {
        TaskLease lease = runningRun();

        assertThatThrownBy(() -> transaction.execute(lease, 1, 0, 0, () -> {
            jdbc.update("insert into task_lease_probe (probe_id, probe_value) values (1, 'rollback')");
            throw new IllegalStateException("domain write failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("select count(*) from task_lease_probe", Long.class)).isZero();
        TaskRun unchanged = repository.findByRunId(lease.runId()).orElseThrow();
        assertThat(unchanged.getProcessedCount()).isZero();
        assertThat(unchanged.getHeartbeatAt()).isEqualTo(STARTED_AT);
    }

    @Test
    void leaseLossRejectsProgressBeforeDomainWorkRuns() {
        TaskLease lease = runningRun();
        AtomicBoolean invoked = new AtomicBoolean();

        assertThatThrownBy(() -> transaction.execute(
                new TaskLease(lease.runId(), UUID.randomUUID()),
                1,
                0,
                0,
                () -> invoked.set(true)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(TASK_RUN_LEASE_LOST));

        assertThat(invoked).isFalse();
        assertThat(repository.findByRunId(lease.runId())).get()
                .extracting(TaskRun::getProcessedCount).isEqualTo(0L);
    }

    private TaskLease runningRun() {
        UUID token = UUID.randomUUID();
        TaskRun run = TaskRun.queued(
                TaskType.CONTENT_SYNC,
                TriggerType.SCHEDULED,
                null,
                UUID.randomUUID(),
                "fingerprint",
                TaskType.CONTENT_SYNC.name(),
                STARTED_AT);
        run.markRunning(token, STARTED_AT);
        repository.saveAndFlush(run);
        return new TaskLease(run.getRunId(), token);
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(UPDATED_AT, ZoneOffset.UTC);
        }
    }
}
