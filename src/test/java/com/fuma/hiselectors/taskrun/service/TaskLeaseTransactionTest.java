package com.fuma.hiselectors.taskrun.service;

import static com.fuma.hiselectors.exception.ErrorCode.TASK_RUN_LEASE_LOST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fuma.hiselectors.config.CacheConfig;
import com.fuma.hiselectors.content.model.ContentReport;
import com.fuma.hiselectors.content.model.ContentReportData;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.model.ContentVersionStatus;
import com.fuma.hiselectors.content.repository.ContentReportRepository;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskStepProgress;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import com.fuma.hiselectors.taskrun.repository.TaskRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
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
    private ContentVersionRepository contentVersionRepository;

    @Autowired
    private ContentReportRepository contentReportRepository;

    @BeforeEach
    void clearData() {
        contentReportRepository.deleteAll();
        contentVersionRepository.deleteAll();
        repository.deleteAll();
    }

    @Test
    void appliesCountsAndHeartbeatAtomicallyForTheCurrentRunningLease() {
        TaskLease lease = runningRun();

        transaction.apply(
                lease,
                "STORE",
                4,
                true,
                "크리에이터 4명 수집",
                2,
                1,
                1,
                Map.of("youtube", new TaskStepProgress(4L, 3L)),
                UPDATED_AT);

        TaskRun updated = repository.findByRunId(lease.runId()).orElseThrow();
        assertThat(updated.getCurrentStep()).isEqualTo("STORE");
        assertThat(updated.getTotalCount()).isEqualTo(4);
        assertThat(updated.getSucceededCount()).isEqualTo(2);
        assertThat(updated.getFailedCount()).isEqualTo(1);
        assertThat(updated.getSkippedCount()).isEqualTo(1);
        assertThat(updated.getProcessedCount()).isEqualTo(4);
        assertThat(updated.getProgressMessage()).isEqualTo("크리에이터 4명 수집");
        assertThat(updated.getStepProgress())
                .containsEntry("youtube", new TaskStepProgress(4L, 3L));
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
                Map.of("youtube", new TaskStepProgress(1L, 1L)),
                UPDATED_AT))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(TASK_RUN_LEASE_LOST));

        assertThat(repository.findByRunId(lease.runId())).get()
                .satisfies(run -> {
                    assertThat(run.getProcessedCount()).isZero();
                    assertThat(run.getStepProgress()).isNull();
                });
    }

    @Test
    void consecutiveAppliesMergeStepProgressWithoutLosingPreviousKeys() {
        TaskLease lease = runningRun();
        transaction.apply(
                lease, null, null, false, null, 0, 0, 0,
                Map.of("youtube", new TaskStepProgress(10L, 2L)), UPDATED_AT);

        transaction.apply(
                lease, null, null, false, null, 0, 0, 0,
                Map.of("instagram", new TaskStepProgress(5L, 1L)), UPDATED_AT.plusSeconds(1));

        assertThat(repository.findByRunId(lease.runId())).get()
                .extracting(TaskRun::getStepProgress)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("youtube", new TaskStepProgress(10L, 2L))
                .containsEntry("instagram", new TaskStepProgress(5L, 1L));
    }

    @Test
    void commitsReportCompletedStatusSucceededCountAndHeartbeatTogether() {
        TaskLease lease = runningRun();
        Long versionId = inspectingVersion();

        transaction.execute(
                lease,
                1,
                0,
                0,
                () -> {
                    ContentVersion version = contentVersionRepository.findById(versionId)
                            .orElseThrow();
                    contentReportRepository.save(ContentReport.create(
                            versionId, ContentReportData.empty(), 9L));
                    version.completeInspection(localDateTime(UPDATED_AT));
                });

        assertThat(contentReportRepository.findFirstByContentVersionIdOrderByIdDesc(versionId))
                .get()
                .extracting(ContentReport::getInspectionPolicyId)
                .isEqualTo(9L);
        assertThat(contentVersionRepository.findById(versionId))
                .get()
                .extracting(ContentVersion::getStatus)
                .isEqualTo(ContentVersionStatus.COMPLETED);
        TaskRun updated = repository.findByRunId(lease.runId()).orElseThrow();
        assertThat(updated.getProcessedCount()).isEqualTo(1);
        assertThat(updated.getSucceededCount()).isEqualTo(1);
        assertThat(updated.getFailedCount()).isZero();
        assertThat(updated.getHeartbeatAt()).isEqualTo(UPDATED_AT);
    }

    @Test
    void callbackErrorRollsBackReportStatusCountsAndHeartbeat() {
        TaskLease lease = runningRun();
        Long versionId = inspectingVersion();

        assertThatThrownBy(() -> transaction.execute(lease, 1, 0, 0, () -> {
            ContentVersion version = contentVersionRepository.findById(versionId)
                    .orElseThrow();
            contentReportRepository.save(ContentReport.create(
                    versionId, ContentReportData.empty(), 9L));
            version.completeInspection(localDateTime(UPDATED_AT));
            throw new IllegalStateException("domain write failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(contentReportRepository.count()).isZero();
        assertThat(contentVersionRepository.findById(versionId))
                .get()
                .extracting(ContentVersion::getStatus)
                .isEqualTo(ContentVersionStatus.INSPECTING);
        TaskRun unchanged = repository.findByRunId(lease.runId()).orElseThrow();
        assertThat(unchanged.getProcessedCount()).isZero();
        assertThat(unchanged.getSucceededCount()).isZero();
        assertThat(unchanged.getHeartbeatAt()).isEqualTo(STARTED_AT);
    }

    @Test
    void commitsFailedStatusFailedCountAndHeartbeatTogether() {
        TaskLease lease = runningRun();
        Long versionId = inspectingVersion();

        transaction.execute(lease, 0, 1, 0, () -> {
            ContentVersion version = contentVersionRepository.findById(versionId)
                    .orElseThrow();
            version.failInspection();
        });

        assertThat(contentVersionRepository.findById(versionId))
                .get()
                .extracting(ContentVersion::getStatus)
                .isEqualTo(ContentVersionStatus.FAILED);
        TaskRun updated = repository.findByRunId(lease.runId()).orElseThrow();
        assertThat(updated.getProcessedCount()).isEqualTo(1);
        assertThat(updated.getSucceededCount()).isZero();
        assertThat(updated.getFailedCount()).isEqualTo(1);
        assertThat(updated.getHeartbeatAt()).isEqualTo(UPDATED_AT);
    }

    @Test
    void replacementLeaseBlocksSuccessCallbackBeforeReportOrStatusChanges() {
        TaskLease lease = runningRun();
        Long versionId = inspectingVersion();
        AtomicBoolean invoked = new AtomicBoolean();
        replaceLease(lease);

        assertThatThrownBy(() -> transaction.execute(
                lease,
                1,
                0,
                0,
                () -> {
                    invoked.set(true);
                    ContentVersion version = contentVersionRepository.findById(versionId)
                            .orElseThrow();
                    contentReportRepository.save(ContentReport.create(
                            versionId, ContentReportData.empty(), 9L));
                    version.completeInspection(localDateTime(UPDATED_AT));
                }))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(TASK_RUN_LEASE_LOST));

        assertThat(invoked).isFalse();
        assertThat(contentReportRepository.count()).isZero();
        assertThat(contentVersionRepository.findById(versionId))
                .get()
                .extracting(ContentVersion::getStatus)
                .isEqualTo(ContentVersionStatus.INSPECTING);
        assertThat(repository.findByRunId(lease.runId())).get()
                .satisfies(run -> {
                    assertThat(run.getProcessedCount()).isZero();
                    assertThat(run.getHeartbeatAt()).isEqualTo(UPDATED_AT);
                });
    }

    @Test
    void replacementLeaseBlocksFailureCallbackBeforeStatusChanges() {
        TaskLease lease = runningRun();
        Long versionId = inspectingVersion();
        AtomicBoolean invoked = new AtomicBoolean();
        replaceLease(lease);

        assertThatThrownBy(() -> transaction.execute(lease, 0, 1, 0, () -> {
            invoked.set(true);
            ContentVersion version = contentVersionRepository.findById(versionId)
                    .orElseThrow();
            version.failInspection();
        })).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(TASK_RUN_LEASE_LOST));

        assertThat(invoked).isFalse();
        assertThat(contentVersionRepository.findById(versionId))
                .get()
                .extracting(ContentVersion::getStatus)
                .isEqualTo(ContentVersionStatus.INSPECTING);
        assertThat(repository.findByRunId(lease.runId())).get()
                .satisfies(run -> {
                    assertThat(run.getProcessedCount()).isZero();
                    assertThat(run.getHeartbeatAt()).isEqualTo(UPDATED_AT);
                });
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

    private Long inspectingVersion() {
        ContentVersion version = ContentVersion.create(
                10L,
                1L,
                "hash",
                localDateTime(STARTED_AT));
        version.startInspection();
        return contentVersionRepository.saveAndFlush(version).getId();
    }

    private void replaceLease(TaskLease lease) {
        TaskRun run = repository.findByRunId(lease.runId()).orElseThrow();
        run.markStale(UUID.randomUUID(), true, UPDATED_AT);
        repository.saveAndFlush(run);
    }

    private LocalDateTime localDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
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
