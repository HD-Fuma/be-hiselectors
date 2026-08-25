package com.fuma.hiselectors.taskrun.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.Column;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

class TaskRunTest {

    private static final Instant NOW = Instant.parse("2026-08-23T03:00:00Z");

    @Test
    void exposesOnlyTheSupportedPublicEnumValues() {
        assertThat(TaskType.values()).containsExactly(
                TaskType.CREATOR_SYNC,
                TaskType.CONTENT_SYNC,
                TaskType.APPLICATION_REPORT_GENERATION,
                TaskType.CONTENT_REPORT_GENERATION,
                TaskType.SETTLEMENT_CALCULATION,
                TaskType.KAKAO_MESSAGE_SEND,
                TaskType.PROPOSAL_EMAIL_SEND);
        assertThat(TriggerType.values()).containsExactly(
                TriggerType.ADMIN_TRIGGERED,
                TriggerType.SCHEDULED);
        assertThat(TaskRunStatus.values()).containsExactly(
                TaskRunStatus.QUEUED,
                TaskRunStatus.RUNNING,
                TaskRunStatus.SUCCEEDED,
                TaskRunStatus.PARTIAL_FAILED,
                TaskRunStatus.FAILED,
                TaskRunStatus.STALE);
    }

    @Test
    void adminTriggeredRunRequiresAdminId() {
        assertThatThrownBy(() -> queued(TriggerType.ADMIN_TRIGGERED, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scheduledRunRejectsAdminId() {
        assertThatThrownBy(() -> queued(TriggerType.SCHEDULED, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void startsQueuedAndTransitionsToRunningWithALease() {
        TaskRun run = queued(TriggerType.ADMIN_TRIGGERED, 1L);
        UUID leaseToken = UUID.randomUUID();

        run.markRunning(leaseToken, NOW.plusSeconds(1));

        assertThat(run.getStatus()).isEqualTo(TaskRunStatus.RUNNING);
        assertThat(run.getLeaseToken()).isEqualTo(leaseToken);
        assertThat(run.getStartedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(run.getHeartbeatAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void queuedRunCannotBeCompletedBeforeItStarts() {
        assertThatThrownBy(() -> queued(TriggerType.ADMIN_TRIGGERED, 1L).complete(NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void runningRunCannotBeStartedAgain() {
        TaskRun run = running();

        assertThatThrownBy(() -> run.markRunning(UUID.randomUUID(), NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void progressIsTheSumOfSucceededFailedAndSkippedCounts() {
        TaskRun run = running();
        run.setTotal(6, NOW.plusSeconds(2));

        run.addCounts(2, 1, 3, NOW.plusSeconds(3));

        assertThat(run.getProcessedCount()).isEqualTo(6);
        assertThat(run.getSucceededCount()).isEqualTo(2);
        assertThat(run.getFailedCount()).isEqualTo(1);
        assertThat(run.getSkippedCount()).isEqualTo(3);
        assertThat(run.getHeartbeatAt()).isEqualTo(NOW.plusSeconds(3));
    }

    @Test
    void rejectsNegativeCountDeltasAndNegativeTotals() {
        TaskRun run = running();

        assertThatThrownBy(() -> run.setTotal(-1, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> run.addCounts(-1, 0, 0, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> run.addCounts(0, -1, 0, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> run.addCounts(0, 0, -1, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void processedCountCannotExceedKnownTotal() {
        TaskRun run = running();
        run.setTotal(2, NOW.plusSeconds(2));

        assertThatThrownBy(() -> run.addCounts(2, 1, 0, NOW.plusSeconds(3)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(run.getProcessedCount()).isZero();
    }

    @Test
    void totalCannotBeSetBelowAlreadyProcessedCount() {
        TaskRun run = running();
        run.addCounts(2, 0, 0, NOW.plusSeconds(2));

        assertThatThrownBy(() -> run.setTotal(1, NOW.plusSeconds(3)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void changesProgressMessageWhileRunning() {
        TaskRun run = running();

        run.changeProgressMessage("YouTube 크리에이터 7명 수집", NOW.plusSeconds(2));

        assertThat(run.getProgressMessage()).isEqualTo("YouTube 크리에이터 7명 수집");
        assertThat(run.getHeartbeatAt()).isEqualTo(NOW.plusSeconds(2));
    }

    @Test
    void progressMessageCannotBeNullOrLongerThanFiveHundredCharacters() {
        TaskRun run = running();

        assertThatThrownBy(() -> run.changeProgressMessage(null, NOW.plusSeconds(2)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("진행 메시지는 필수입니다.");
        assertThatThrownBy(() -> run.changeProgressMessage("가".repeat(501), NOW.plusSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("진행 메시지는 500자를 초과할 수 없습니다.");
    }

    @Test
    void runningRunRecordsABoundedFailureSummaryAndCompletionPreservesIt() {
        TaskRun run = running();
        run.addCounts(2, 1, 0, NOW.plusSeconds(2));

        run.recordFailure("REMOTE_ITEM_REJECTED", "원격 항목 17 처리 실패", NOW.plusSeconds(3));
        run.complete(NOW.plusSeconds(4));

        assertThat(run.getStatus()).isEqualTo(TaskRunStatus.PARTIAL_FAILED);
        assertThat(run.getErrorType()).isEqualTo("REMOTE_ITEM_REJECTED");
        assertThat(run.getErrorMessage()).isEqualTo("원격 항목 17 처리 실패");
        assertThat(run.getSucceededCount()).isEqualTo(2);
        assertThat(run.getFailedCount()).isEqualTo(1);
        assertThat(run.getHeartbeatAt()).isEqualTo(NOW.plusSeconds(4));
    }

    @Test
    void failureSummaryRequiresValuesWithinDomainLimits() {
        TaskRun run = running();

        assertThatThrownBy(() -> run.recordFailure(null, "message", NOW.plusSeconds(2)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("오류 유형은 필수입니다.");
        assertThatThrownBy(() -> run.recordFailure("ERROR", null, NOW.plusSeconds(2)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("오류 메시지는 필수입니다.");
        assertThatThrownBy(() -> run.recordFailure("가".repeat(101), "message", NOW.plusSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("오류 유형은 100자를 초과할 수 없습니다.");
        assertThatThrownBy(() -> run.recordFailure("ERROR", "가".repeat(501), NOW.plusSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("오류 메시지는 500자를 초과할 수 없습니다.");
        assertThat(run.getErrorType()).isNull();
        assertThat(run.getErrorMessage()).isNull();
    }

    @Test
    void failureSummaryRejectsBlankTypeAndMessage() {
        TaskRun run = running();

        assertThatThrownBy(() -> run.recordFailure("", "message", NOW.plusSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("오류 유형은 비어 있을 수 없습니다.");
        assertThatThrownBy(() -> run.recordFailure("   ", "message", NOW.plusSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("오류 유형은 비어 있을 수 없습니다.");
        assertThatThrownBy(() -> run.recordFailure("ERROR", "", NOW.plusSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("오류 메시지는 비어 있을 수 없습니다.");
        assertThatThrownBy(() -> run.recordFailure("ERROR", "   ", NOW.plusSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("오류 메시지는 비어 있을 수 없습니다.");
        assertThat(run.getErrorType()).isNull();
        assertThat(run.getErrorMessage()).isNull();
    }

    @Test
    void stepProgressMergeRetainsExistingKeysAndReplacesPatchedKeys() {
        TaskRun run = running();
        LinkedHashMap<String, TaskStepProgress> firstPatch = new LinkedHashMap<>();
        firstPatch.put("youtube", new TaskStepProgress(10L, 2L));
        firstPatch.put("instagram", new TaskStepProgress(null, 1L));
        run.mergeStepProgress(firstPatch, NOW.plusSeconds(2));

        run.mergeStepProgress(
                Map.of("youtube", new TaskStepProgress(10L, 7L)),
                NOW.plusSeconds(3));
        firstPatch.clear();

        assertThat(run.getStepProgress()).containsExactly(
                org.assertj.core.api.Assertions.entry(
                        "youtube", new TaskStepProgress(10L, 7L)),
                org.assertj.core.api.Assertions.entry(
                        "instagram", new TaskStepProgress(null, 1L)));
        assertThatThrownBy(() -> run.getStepProgress().put(
                "store", new TaskStepProgress(1L, 1L)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(run.getHeartbeatAt()).isEqualTo(NOW.plusSeconds(3));
    }

    @Test
    void stepProgressRejectsInvalidKeysBeforeMutation() {
        TaskRun run = running();

        assertThatThrownBy(() -> run.mergeStepProgress(
                Map.of(" ", new TaskStepProgress(1L, 0L)), NOW.plusSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("단계 키는 비어 있을 수 없습니다.");
        assertThatThrownBy(() -> run.mergeStepProgress(
                Map.of("가".repeat(101), new TaskStepProgress(1L, 0L)), NOW.plusSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("단계 키는 100자를 초과할 수 없습니다.");
        assertThat(run.getStepProgress()).isNull();
    }

    @Test
    void completionUsesFailedThenPartialFailedThenSucceededPrecedence() {
        TaskRun failed = running();
        failed.addCounts(0, 2, 1, NOW.plusSeconds(2));
        failed.complete(NOW.plusSeconds(3));

        TaskRun partial = running();
        partial.addCounts(1, 1, 0, NOW.plusSeconds(2));
        partial.complete(NOW.plusSeconds(3));

        TaskRun succeeded = running();
        succeeded.addCounts(1, 0, 1, NOW.plusSeconds(2));
        succeeded.complete(NOW.plusSeconds(3));

        assertThat(failed.getStatus()).isEqualTo(TaskRunStatus.FAILED);
        assertThat(partial.getStatus()).isEqualTo(TaskRunStatus.PARTIAL_FAILED);
        assertThat(succeeded.getStatus()).isEqualTo(TaskRunStatus.SUCCEEDED);
    }

    @Test
    void failRecordsErrorAndFinishesTheRun() {
        TaskRun run = running();
        run.addCounts(1, 1, 1, NOW.plusSeconds(2));
        run.recordFailure("SAFE_ITEM_FAILURE", "항목 요약", NOW.plusSeconds(3));

        run.fail("REMOTE_TIMEOUT", "upstream did not respond", NOW.plusSeconds(4));

        assertThat(run.getStatus()).isEqualTo(TaskRunStatus.FAILED);
        assertThat(run.getErrorType()).isEqualTo("REMOTE_TIMEOUT");
        assertThat(run.getErrorMessage()).isEqualTo("upstream did not respond");
        assertThat(run.getSucceededCount()).isEqualTo(1);
        assertThat(run.getFailedCount()).isEqualTo(1);
        assertThat(run.getSkippedCount()).isEqualTo(1);
        assertThat(run.getFinishedAt()).isEqualTo(NOW.plusSeconds(4));
        assertThat(run.isTerminal()).isTrue();
    }

    @Test
    void markStaleReplacesLeaseAndCanReleaseConcurrencyKey() {
        TaskRun run = running();
        UUID replacementToken = UUID.randomUUID();

        run.markStale(replacementToken, true, NOW.plusSeconds(2));

        assertThat(run.getStatus()).isEqualTo(TaskRunStatus.STALE);
        assertThat(run.getLeaseToken()).isEqualTo(replacementToken);
        assertThat(run.getConcurrencyKey()).isNull();
        assertThat(run.getFinishedAt()).isEqualTo(NOW.plusSeconds(2));
    }

    @Test
    void markStaleCanKeepConcurrencyKeyUntilTheReplacementIsFenced() {
        TaskRun run = running();

        run.markStale(UUID.randomUUID(), false, NOW.plusSeconds(2));

        assertThat(run.getConcurrencyKey()).isEqualTo("content-sync");
    }

    @Test
    void terminalRunsAreImmutable() {
        TaskRun run = running();
        run.complete(NOW.plusSeconds(2));

        assertThatThrownBy(() -> run.setTotal(1, NOW.plusSeconds(3)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> run.changeStep("next", NOW.plusSeconds(3)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> run.changeProgressMessage("message", NOW.plusSeconds(3)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> run.recordFailure("ERROR", "message", NOW.plusSeconds(3)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> run.mergeStepProgress(
                Map.of("step", new TaskStepProgress(1L, 1L)), NOW.plusSeconds(3)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> run.addCounts(1, 0, 0, NOW.plusSeconds(3)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> run.fail("ERROR", "message", NOW.plusSeconds(3)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> run.markStale(UUID.randomUUID(), false, NOW.plusSeconds(3)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void taskRunHasNoPublicSetters() {
        assertThat(Arrays.stream(TaskRun.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .filter(name -> name.startsWith("set")))
                .containsExactly("setTotal");
    }

    @Test
    void heartbeatIsMappedAsNotNull() throws NoSuchFieldException {
        Column column = TaskRun.class.getDeclaredField("heartbeatAt").getAnnotation(Column.class);

        assertThat(column.nullable()).isFalse();
    }

    @Test
    void progressMessageIsMappedAsNullableVarcharFiveHundred() throws NoSuchFieldException {
        Column column = TaskRun.class.getDeclaredField("progressMessage").getAnnotation(Column.class);

        assertThat(column.nullable()).isTrue();
        assertThat(column.length()).isEqualTo(500);
    }

    @Test
    void stepProgressIsMappedAsNullableJson() throws NoSuchFieldException {
        var field = TaskRun.class.getDeclaredField("stepProgress");
        Column column = field.getAnnotation(Column.class);
        JdbcTypeCode jdbcTypeCode = field.getAnnotation(JdbcTypeCode.class);

        assertThat(column.name()).isEqualTo("step_progress");
        assertThat(column.nullable()).isTrue();
        assertThat(jdbcTypeCode.value()).isEqualTo(SqlTypes.JSON);
    }

    private TaskRun running() {
        TaskRun run = queued(TriggerType.ADMIN_TRIGGERED, 1L);
        run.markRunning(UUID.randomUUID(), NOW.plusSeconds(1));
        return run;
    }

    private TaskRun queued(TriggerType trigger, Long adminId) {
        return TaskRun.queued(
                TaskType.CONTENT_SYNC,
                trigger,
                adminId,
                UUID.randomUUID(),
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "content-sync",
                NOW);
    }
}
