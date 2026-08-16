package com.fuma.hiselectors.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
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
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), SEOUL);
        SettlementPaymentService service = new SettlementPaymentService(
                historyRepository, paymentWorker, clock);

        SettlementHistory first = mock(SettlementHistory.class);
        SettlementHistory second = mock(SettlementHistory.class);
        SettlementHistory third = mock(SettlementHistory.class);
        when(first.getId()).thenReturn(1L);
        when(second.getId()).thenReturn(2L);
        when(third.getId()).thenReturn(3L);
        when(historyRepository.findAllBySettlementMonthAndStatus(
                LocalDateTime.of(2026, 6, 1, 0, 0), SettlementStatus.PAYMENT_PENDING))
                .thenReturn(List.of(first, second, third));
        when(paymentWorker.process(any())).thenReturn(
                SettlementPaymentWorker.PaymentOutcome.SETTLED,
                SettlementPaymentWorker.PaymentOutcome.HELD,
                SettlementPaymentWorker.PaymentOutcome.HELD);

        var result = service.process(YearMonth.of(2026, 6));

        assertThat(result.targetSettlementMonth()).isEqualTo(YearMonth.of(2026, 6));
        assertThat(result.processedCount()).isEqualTo(3);
        assertThat(result.settledCount()).isEqualTo(1);
        assertThat(result.heldCount()).isEqualTo(2);
        assertThat(result.skippedCount()).isZero();
        assertThat(result.failedCount()).isZero();
        verify(paymentWorker, org.mockito.Mockito.times(3)).process(anyLong());
    }
}
