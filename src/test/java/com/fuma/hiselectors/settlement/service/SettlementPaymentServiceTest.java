package com.fuma.hiselectors.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
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
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), SEOUL);
        SettlementPaymentService service = new SettlementPaymentService(
                historyRepository, paymentWorker, notificationService,
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
                .findAllByStatusAndActivityYearMonthLessThanEqualOrderByActivityYearMonthAsc(
                        SettlementStatus.PAYMENT_PENDING, 202604))
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
    }

    @Test
    void continuesPaymentProcessingWhenOneHoldReopenFails() {
        SettlementHistoryRepository historyRepository = mock(SettlementHistoryRepository.class);
        SettlementPaymentWorker paymentWorker = mock(SettlementPaymentWorker.class);
        SettlementMissingNotificationService notificationService = mock(
                SettlementMissingNotificationService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), SEOUL);
        SettlementPaymentService service = new SettlementPaymentService(
                historyRepository, paymentWorker, notificationService,
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
                .findAllByStatusAndActivityYearMonthLessThanEqualOrderByActivityYearMonthAsc(
                        SettlementStatus.PAYMENT_PENDING, 202604))
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
        Clock clock = Clock.fixed(Instant.parse("2026-08-21T03:00:00Z"), SEOUL);
        SettlementPaymentService service = new SettlementPaymentService(
                historyRepository, paymentWorker, notificationService,
                new SettlementSchedulePolicy(), clock);
        SettlementHistory pending = mock(SettlementHistory.class);
        when(pending.getId()).thenReturn(6L);
        when(historyRepository.findAllByStatusIn(EnumSet.of(
                SettlementStatus.PAYMENT_HOLD_INFO, SettlementStatus.PAYMENT_HOLD_BLACK)))
                .thenReturn(List.of());
        when(historyRepository
                .findAllByStatusAndActivityYearMonthLessThanEqualOrderByActivityYearMonthAsc(
                        SettlementStatus.PAYMENT_PENDING, 202606))
                .thenReturn(List.of(pending));
        when(paymentWorker.process(6L))
                .thenReturn(SettlementPaymentWorker.PaymentOutcome.SETTLED);

        SettlementPaymentResponse result = service.processCurrentPaymentMonth();

        assertThat(result.latestEligibleActivityMonth()).isEqualTo(YearMonth.of(2026, 6));
        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.settledCount()).isEqualTo(1);
        verify(paymentWorker).process(6L);
    }
}
