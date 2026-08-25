package com.fuma.hiselectors.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.dto.SettlementPaymentResponse;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettlementPaymentServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Test
    void aggregatesIndependentPaymentWorkerResults() {
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SettlementPaymentWorker paymentWorker = mock(SettlementPaymentWorker.class);
        SettlementMissingNotificationService notificationService = mock(
                SettlementMissingNotificationService.class);
        SettlementStatusNotificationService statusNotificationService = mock(
                SettlementStatusNotificationService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), SEOUL);
        SettlementPaymentService service = new SettlementPaymentService(
                historyRepository, paymentWorker, notificationService, statusNotificationService,
                new SettlementSchedulePolicy(), clock);

        SettlementHistory first = mock(SettlementHistory.class);
        SettlementHistory second = mock(SettlementHistory.class);
        SettlementHistory third = mock(SettlementHistory.class);
        when(first.getId()).thenReturn(1L);
        when(second.getId()).thenReturn(2L);
        when(third.getId()).thenReturn(3L);
        when(historyRepository.findAllByStatusIn(EnumSet.of(
                SettlementStatus.PAYMENT_HOLD_INFO, SettlementStatus.PAYMENT_HOLD_BLACK)))
                .thenReturn(List.of());
        when(historyRepository
                .findAllPayablePending(
                        SettlementStatus.PAYMENT_PENDING, 202604, 202606))
                .thenReturn(List.of(first, second, third));
        when(paymentWorker.process(any())).thenReturn(
                SettlementPaymentWorker.PaymentOutcome.SETTLED,
                SettlementPaymentWorker.PaymentOutcome.HELD_INFO,
                SettlementPaymentWorker.PaymentOutcome.HELD_BLACK);

        var result = service.process(YearMonth.of(2026, 6));

        assertThat(result.paymentMonth()).isEqualTo(YearMonth.of(2026, 6));
        assertThat(result.latestEligibleActivityMonth()).isEqualTo(YearMonth.of(2026, 4));
        assertThat(result.processedCount()).isEqualTo(3);
        assertThat(result.settledCount()).isEqualTo(1);
        assertThat(result.heldCount()).isEqualTo(2);
        assertThat(result.skippedCount()).isZero();
        assertThat(result.failedCount()).isZero();
        verify(paymentWorker, org.mockito.Mockito.times(3)).process(anyLong());
        verify(statusNotificationService).notifyCompleted(1L);
    }

    @Test
    void continuesPaymentProcessingWhenOneHoldReopenFails() {
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SettlementPaymentWorker paymentWorker = mock(SettlementPaymentWorker.class);
        SettlementMissingNotificationService notificationService = mock(
                SettlementMissingNotificationService.class);
        SettlementStatusNotificationService statusNotificationService = mock(
                SettlementStatusNotificationService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), SEOUL);
        SettlementPaymentService service = new SettlementPaymentService(
                historyRepository, paymentWorker, notificationService, statusNotificationService,
                new SettlementSchedulePolicy(), clock);
        SettlementHistory hold = mock(SettlementHistory.class);
        SettlementHistory pending = mock(SettlementHistory.class);
        when(hold.getId()).thenReturn(1L);
        when(pending.getId()).thenReturn(2L);
        when(historyRepository.findAllByStatusIn(EnumSet.of(
                SettlementStatus.PAYMENT_HOLD_INFO, SettlementStatus.PAYMENT_HOLD_BLACK)))
                .thenReturn(List.of(hold));
        org.mockito.Mockito.doThrow(new IllegalStateException("lock timeout"))
                .when(paymentWorker).reopenIfResolved(1L);
        when(historyRepository
                .findAllPayablePending(
                        SettlementStatus.PAYMENT_PENDING, 202604, 202606))
                .thenReturn(List.of(pending));
        when(paymentWorker.process(2L)).thenReturn(SettlementPaymentWorker.PaymentOutcome.SETTLED);

        SettlementPaymentResponse result = service.process(YearMonth.of(2026, 6));

        assertThat(result.settledCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
        verify(paymentWorker).process(2L);
    }

    @Test
    void catchesUpMissedPaymentAfterTheScheduledPaymentDate() {
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SettlementPaymentWorker paymentWorker = mock(SettlementPaymentWorker.class);
        SettlementMissingNotificationService notificationService = mock(
                SettlementMissingNotificationService.class);
        SettlementStatusNotificationService statusNotificationService = mock(
                SettlementStatusNotificationService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-21T03:00:00Z"), SEOUL);
        SettlementPaymentService service = new SettlementPaymentService(
                historyRepository, paymentWorker, notificationService, statusNotificationService,
                new SettlementSchedulePolicy(), clock);
        SettlementHistory pending = mock(SettlementHistory.class);
        when(pending.getId()).thenReturn(6L);
        when(historyRepository.findAllByStatusIn(EnumSet.of(
                SettlementStatus.PAYMENT_HOLD_INFO, SettlementStatus.PAYMENT_HOLD_BLACK)))
                .thenReturn(List.of());
        when(historyRepository
                .findAllPayablePending(
                        SettlementStatus.PAYMENT_PENDING, 202606, 202608))
                .thenReturn(List.of(pending));
        when(paymentWorker.process(6L))
                .thenReturn(SettlementPaymentWorker.PaymentOutcome.SETTLED);

        SettlementPaymentResponse result = service.processCurrentPaymentMonth();

        assertThat(result.latestEligibleActivityMonth()).isEqualTo(YearMonth.of(2026, 6));
        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.settledCount()).isEqualTo(1);
        verify(paymentWorker).process(6L);
    }

    @Test
    void keepsSettlementSuccessfulWhenCompletedNotificationFails() {
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SettlementPaymentWorker paymentWorker = mock(SettlementPaymentWorker.class);
        SettlementMissingNotificationService notificationService = mock(
                SettlementMissingNotificationService.class);
        SettlementStatusNotificationService statusNotificationService = mock(
                SettlementStatusNotificationService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), SEOUL);
        SettlementPaymentService service = new SettlementPaymentService(
                historyRepository, paymentWorker, notificationService, statusNotificationService,
                new SettlementSchedulePolicy(), clock);
        SettlementHistory pending = mock(SettlementHistory.class);
        when(pending.getId()).thenReturn(7L);
        when(historyRepository.findAllByStatusIn(EnumSet.of(
                SettlementStatus.PAYMENT_HOLD_INFO, SettlementStatus.PAYMENT_HOLD_BLACK)))
                .thenReturn(List.of());
        when(historyRepository
                .findAllPayablePending(
                        SettlementStatus.PAYMENT_PENDING, 202606, 202608))
                .thenReturn(List.of(pending));
        when(paymentWorker.process(7L))
                .thenReturn(SettlementPaymentWorker.PaymentOutcome.SETTLED);
        doThrow(new IllegalStateException("notification failed"))
                .when(statusNotificationService).notifyCompleted(7L);

        SettlementPaymentResponse result = service.processCurrentPaymentMonth();

        assertThat(result.settledCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
    }

    @Test
    void processesScheduledHistoriesAsOneGroupAndNotifiesEachMonth() {
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SettlementPaymentWorker paymentWorker = mock(SettlementPaymentWorker.class);
        SettlementMissingNotificationService missingNotificationService = mock(
                SettlementMissingNotificationService.class);
        SettlementStatusNotificationService statusNotificationService = mock(
                SettlementStatusNotificationService.class);
        SettlementPaymentService service = new SettlementPaymentService(
                historyRepository, paymentWorker, missingNotificationService,
                statusNotificationService, new SettlementSchedulePolicy(),
                Clock.fixed(Instant.parse("2026-09-20T00:00:00Z"), SEOUL));
        SettlementHistory first = mock(SettlementHistory.class);
        SettlementHistory second = mock(SettlementHistory.class);
        when(first.getId()).thenReturn(11L);
        when(first.getSelectorsId()).thenReturn(3L);
        when(first.getScheduledPaymentYearMonth()).thenReturn(202609);
        when(second.getId()).thenReturn(12L);
        when(second.getSelectorsId()).thenReturn(3L);
        when(second.getScheduledPaymentYearMonth()).thenReturn(202609);
        when(historyRepository.findAllByStatusIn(any())).thenReturn(List.of());
        when(historyRepository.findAllPayablePending(
                SettlementStatus.PAYMENT_PENDING, 202607, 202609))
                .thenReturn(List.of(first, second));
        when(paymentWorker.processGroup(List.of(11L, 12L)))
                .thenReturn(SettlementPaymentWorker.PaymentOutcome.SETTLED);

        SettlementPaymentResponse result = service.process(YearMonth.of(2026, 9));

        assertThat(result.processedCount()).isEqualTo(2);
        assertThat(result.settledCount()).isEqualTo(2);
        verify(statusNotificationService).notifyCompleted(11L);
        verify(statusNotificationService).notifyCompleted(12L);
    }
}
