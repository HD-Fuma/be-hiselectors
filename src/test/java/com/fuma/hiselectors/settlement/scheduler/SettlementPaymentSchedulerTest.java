package com.fuma.hiselectors.settlement.scheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.settlement.model.SettlementHistory;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import com.fuma.hiselectors.settlement.service.SettlementPaymentService;
import com.fuma.hiselectors.settlement.service.SettlementSchedulePolicy;
import com.fuma.hiselectors.settlement.service.SettlementStatusNotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SettlementPaymentSchedulerTest {

    @Test
    void sendsUpcomingNoticeThreeDaysBeforeActualPaymentDate() {
        SettlementPaymentService paymentService = mock(SettlementPaymentService.class);
        SettlementHistoryRepository historyRepository =
                mock(SettlementHistoryRepository.class);
        SettlementStatusNotificationService notificationService =
                mock(SettlementStatusNotificationService.class);
        SettlementHistory history = SettlementHistory.create(
                2L, LocalDateTime.of(2026, 6, 1, 0, 0));
        ReflectionTestUtils.setField(history, "id", 10L);
        when(historyRepository
                .findAllByStatusAndActivityYearMonthAndSettlementAmountGreaterThan(
                        SettlementStatus.PAYMENT_PENDING, 202606, 0L))
                .thenReturn(List.of(history));
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-16T15:00:00Z"), ZoneId.of("Asia/Seoul"));
        SettlementPaymentScheduler scheduler = new SettlementPaymentScheduler(
                paymentService, historyRepository, notificationService,
                new SettlementSchedulePolicy(), clock);

        scheduler.notifyUpcomingSettlements();

        verify(notificationService).notifyUpcoming(
                10L, java.time.LocalDate.of(2026, 8, 20));
    }

    @Test
    void skipsUpcomingNoticeOnOtherDays() {
        SettlementPaymentService paymentService = mock(SettlementPaymentService.class);
        SettlementHistoryRepository historyRepository =
                mock(SettlementHistoryRepository.class);
        SettlementStatusNotificationService notificationService =
                mock(SettlementStatusNotificationService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-15T15:00:00Z"), ZoneId.of("Asia/Seoul"));
        SettlementPaymentScheduler scheduler = new SettlementPaymentScheduler(
                paymentService, historyRepository, notificationService,
                new SettlementSchedulePolicy(), clock);

        scheduler.notifyUpcomingSettlements();

        verify(historyRepository, never())
                .findAllByStatusAndActivityYearMonthAndSettlementAmountGreaterThan(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }
}
