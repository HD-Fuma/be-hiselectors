package com.fuma.hiselectors.taskrun.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fuma.hiselectors.taskrun.config.TaskRunProperties;
import com.fuma.hiselectors.taskrun.model.TaskStepProgress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class TaskProgressReporterTest {

    private static final Instant NOW = Instant.parse("2026-08-23T03:00:00Z");

    private final TaskLease lease = new TaskLease(UUID.randomUUID(), UUID.randomUUID());
    private final TaskLeaseTransaction transaction = mock(TaskLeaseTransaction.class);
    private final TaskRunProgressStream progressStream = mock(TaskRunProgressStream.class);
    private final MutableClock clock = new MutableClock(NOW);
    private ThrottledTaskProgressReporter reporter;

    @BeforeEach
    void setUp() {
        reporter = new ThrottledTaskProgressReporter(
                lease,
                transaction,
                progressStream,
                new TaskRunProperties.Progress(10, 2_000),
                clock);
    }

    @Test
    void startPersistsTheStepImmediatelyAndAllowsAnUnknownTotal() {
        reporter.start("LOAD", null);

        verify(transaction).apply(lease, "LOAD", null, true, null, 0, 0, 0, Map.of(), NOW);
    }

    @Test
    void persistsAfterTenAccumulatedItems() {
        reporter.start("LOAD", 20);
        clearInvocations(transaction);

        reporter.advance(4, 3, 2);
        verify(transaction, never()).apply(
                lease, null, null, false, null, 4, 3, 2, Map.of(), NOW);

        reporter.advance(1, 0, 0);

        verify(transaction).apply(lease, null, null, false, null, 5, 3, 2, Map.of(), NOW);
        verifyNoInteractions(progressStream);
    }

    @Test
    void heartbeatFlushesPendingCountsAfterTwoElapsedSeconds() {
        reporter.start("LOAD", null);
        reporter.advance(1, 1, 0);
        clearInvocations(transaction);
        clock.advanceSeconds(2);

        reporter.heartbeat();

        verify(transaction).apply(
                lease, null, null, false, null, 1, 1, 0, Map.of(), NOW.plusSeconds(2));
    }

    @Test
    void changingStepPersistsPendingCountsWithoutResettingTheRunCounters() {
        reporter.start("LOAD", null);
        reporter.advance(2, 1, 0);
        clearInvocations(transaction);

        reporter.changeStep("STORE");

        verify(transaction).apply(lease, "STORE", null, false, null, 2, 1, 0, Map.of(), NOW);

        reporter.advance(4, 0, 0);
        reporter.flush();

        verify(transaction).apply(lease, null, null, false, null, 4, 0, 0, Map.of(), NOW);
    }

    @Test
    void finalInternalFlushPersistsItemsBelowBothThresholds() {
        reporter.start("LOAD", 10);
        reporter.advance(1, 0, 0);
        clearInvocations(transaction);

        reporter.flush();

        verify(transaction).apply(lease, null, null, false, null, 1, 0, 0, Map.of(), NOW);
    }

    @Test
    void countFlushPersistsOnlyTheLatestProgressMessage() {
        reporter.describe("크리에이터 1명 수집");
        reporter.describe("크리에이터 2명 수집");
        verifyNoInteractions(transaction);

        reporter.advance(10, 0, 0);

        verify(transaction).apply(
                lease, null, null, false, "크리에이터 2명 수집", 10, 0, 0, Map.of(), NOW);
    }

    @Test
    void messageOnlyFlushPersistsThePendingMessage() {
        reporter.describe("YouTube 크리에이터 7명 수집");

        reporter.flush();

        verify(transaction).apply(
                lease, null, null, false,
                "YouTube 크리에이터 7명 수집", 0, 0, 0, Map.of(), NOW);
    }

    @Test
    void rejectsAnOversizedMessageBeforeItBecomesPending() {
        assertThatThrownBy(() -> reporter.describe("가".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("진행 메시지는 500자를 초과할 수 없습니다.");

        reporter.flush();
        verifyNoInteractions(transaction);
    }

    @Test
    void successfulFlushClearsThePendingMessage() {
        reporter.describe("YouTube 크리에이터 7명 수집");
        reporter.flush();
        clearInvocations(transaction);

        reporter.advance(1, 0, 0);
        reporter.flush();

        verify(transaction).apply(lease, null, null, false, null, 1, 0, 0, Map.of(), NOW);
    }

    @Test
    void failedFlushKeepsThePendingMessageForRetry() {
        reporter.describe("YouTube 크리에이터 7명 수집");
        doThrow(new IllegalStateException("temporary"))
                .doNothing()
                .when(transaction)
                .apply(lease, null, null, false,
                        "YouTube 크리에이터 7명 수집", 0, 0, 0, Map.of(), NOW);

        assertThatThrownBy(reporter::flush)
                .isInstanceOf(IllegalStateException.class);
        reporter.flush();

        verify(transaction, times(2)).apply(
                lease, null, null, false,
                "YouTube 크리에이터 7명 수집", 0, 0, 0, Map.of(), NOW);
    }

    @Test
    void reportStepBuffersLatestValuesUntilAnotherOperationFlushes() {
        reporter.reportStep("youtube", 10L, 2L);
        reporter.reportStep("youtube", 10L, 4L);
        reporter.reportStep("instagram", null, 1L);
        verifyNoInteractions(transaction);

        reporter.changeStep("STORE");

        verify(transaction).apply(
                lease,
                "STORE",
                null,
                false,
                null,
                0,
                0,
                0,
                Map.of(
                        "youtube", new TaskStepProgress(10L, 4L),
                        "instagram", new TaskStepProgress(null, 1L)),
                NOW);
    }

    @Test
    void contentStepSnapshotsPublishAbsoluteProgressImmediatelyWithoutDatabaseWrites() {
        reporter.reportStep("NEW_CONTENT_SYNC", null, 1L);
        reporter.reportStep("NEW_CONTENT_SYNC", null, 2L);
        reporter.reportStep("NEW_CONTENT_SYNC", 3L, 3L);

        InOrder order = org.mockito.Mockito.inOrder(progressStream);
        order.verify(progressStream).publish(
                new TaskRunProgressEvent(lease.runId(), "NEW_CONTENT_SYNC", null, 1L));
        order.verify(progressStream).publish(
                new TaskRunProgressEvent(lease.runId(), "NEW_CONTENT_SYNC", null, 2L));
        order.verify(progressStream).publish(
                new TaskRunProgressEvent(lease.runId(), "NEW_CONTENT_SYNC", 3L, 3L));
        verifyNoInteractions(transaction);
    }

    @Test
    void nonContentStepSnapshotStaysOffTheProgressStream() {
        reporter.reportStep("youtube", 3L, 1L);

        verifyNoInteractions(progressStream);
        verifyNoInteractions(transaction);
    }

    @Test
    void progressStreamFailureLeavesTheWorkerAndPendingSnapshotHealthy() {
        doThrow(new IllegalStateException("stream unavailable"))
                .when(progressStream)
                .publish(new TaskRunProgressEvent(
                        lease.runId(), "STORED_CONTENT_SYNC", 3L, 1L));

        reporter.reportStep("STORED_CONTENT_SYNC", 3L, 1L);
        reporter.flush();

        verify(transaction).apply(
                lease,
                null,
                null,
                false,
                null,
                0,
                0,
                0,
                Map.of("STORED_CONTENT_SYNC", new TaskStepProgress(3L, 1L)),
                NOW);
    }

    @Test
    void heartbeatAndFinalFlushPersistPendingStepProgress() {
        reporter.reportStep("youtube", 10L, 3L);

        reporter.heartbeat();

        verify(transaction).apply(
                lease,
                null,
                null,
                false,
                null,
                0,
                0,
                0,
                Map.of("youtube", new TaskStepProgress(10L, 3L)),
                NOW);
        clearInvocations(transaction);
        reporter.reportStep("instagram", 5L, 2L);

        reporter.flush();

        verify(transaction).apply(
                lease,
                null,
                null,
                false,
                null,
                0,
                0,
                0,
                Map.of("instagram", new TaskStepProgress(5L, 2L)),
                NOW);
    }

    @Test
    void failedFlushKeepsPendingStepProgressForRetry() {
        Map<String, TaskStepProgress> patch =
                Map.of("youtube", new TaskStepProgress(10L, 3L));
        reporter.reportStep("youtube", 10L, 3L);
        doThrow(new IllegalStateException("temporary"))
                .doNothing()
                .when(transaction)
                .apply(lease, null, null, false, null, 0, 0, 0, patch, NOW);

        assertThatThrownBy(reporter::flush).isInstanceOf(IllegalStateException.class);
        reporter.flush();

        verify(transaction, times(2))
                .apply(lease, null, null, false, null, 0, 0, 0, patch, NOW);
    }

    @Test
    void reportStepRejectsInvalidKeysAndCountsBeforeBuffering() {
        assertThatThrownBy(() -> reporter.reportStep(" ", 1L, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("단계 키는 비어 있을 수 없습니다.");
        assertThatThrownBy(() -> reporter.reportStep("가".repeat(101), 1L, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("단계 키는 100자를 초과할 수 없습니다.");
        assertThatThrownBy(() -> reporter.reportStep("youtube", 1L, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("단계 처리 건수는 전체 건수를 초과할 수 없습니다.");

        reporter.flush();
        verifyNoInteractions(transaction);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
