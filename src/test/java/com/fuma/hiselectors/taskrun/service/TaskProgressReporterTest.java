package com.fuma.hiselectors.taskrun.service;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fuma.hiselectors.taskrun.config.TaskRunProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskProgressReporterTest {

    private static final Instant NOW = Instant.parse("2026-08-23T03:00:00Z");

    private final TaskLease lease = new TaskLease(UUID.randomUUID(), UUID.randomUUID());
    private final TaskLeaseTransaction transaction = mock(TaskLeaseTransaction.class);
    private final MutableClock clock = new MutableClock(NOW);
    private ThrottledTaskProgressReporter reporter;

    @BeforeEach
    void setUp() {
        reporter = new ThrottledTaskProgressReporter(
                lease,
                transaction,
                new TaskRunProperties.Progress(10, 2_000),
                clock);
    }

    @Test
    void startPersistsTheStepImmediatelyAndAllowsAnUnknownTotal() {
        reporter.start("LOAD", null);

        verify(transaction).apply(lease, "LOAD", null, true, 0, 0, 0, NOW);
    }

    @Test
    void persistsAfterTenAccumulatedItems() {
        reporter.start("LOAD", 20);
        clearInvocations(transaction);

        reporter.advance(4, 3, 2);
        verify(transaction, never()).apply(lease, null, null, false, 4, 3, 2, NOW);

        reporter.advance(1, 0, 0);

        verify(transaction).apply(lease, null, null, false, 5, 3, 2, NOW);
    }

    @Test
    void heartbeatFlushesPendingCountsAfterTwoElapsedSeconds() {
        reporter.start("LOAD", null);
        reporter.advance(1, 1, 0);
        clearInvocations(transaction);
        clock.advanceSeconds(2);

        reporter.heartbeat();

        verify(transaction).apply(lease, null, null, false, 1, 1, 0, NOW.plusSeconds(2));
    }

    @Test
    void changingStepPersistsPendingCountsWithoutResettingTheRunCounters() {
        reporter.start("LOAD", null);
        reporter.advance(2, 1, 0);
        clearInvocations(transaction);

        reporter.changeStep("STORE");

        verify(transaction).apply(lease, "STORE", null, false, 2, 1, 0, NOW);

        reporter.advance(4, 0, 0);
        reporter.flush();

        verify(transaction).apply(lease, null, null, false, 4, 0, 0, NOW);
    }

    @Test
    void finalInternalFlushPersistsItemsBelowBothThresholds() {
        reporter.start("LOAD", 10);
        reporter.advance(1, 0, 0);
        clearInvocations(transaction);

        reporter.flush();

        verify(transaction).apply(lease, null, null, false, 1, 0, 0, NOW);
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
