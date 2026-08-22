package com.fuma.hiselectors.performance.notification;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.purchase.repository.PurchaseHistoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class PerformanceNotificationSchedulerTest {

    @Test
    void checksWeeklyGrowthForLastWeeksSalesSelectorsOnMonday() {
        PurchaseHistoryRepository purchaseHistoryRepository =
                mock(PurchaseHistoryRepository.class);
        PerformanceNotificationService notificationService =
                mock(PerformanceNotificationService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-24T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        when(purchaseHistoryRepository.findDistinctSelectorsIdsByStatusAndConfirmedAtBetween(
                PurchaseStatus.PURCHASE_CONFIRMED,
                LocalDateTime.of(2026, 8, 17, 0, 0),
                LocalDateTime.of(2026, 8, 24, 0, 0)))
                .thenReturn(List.of(2L, 3L));
        PerformanceNotificationScheduler scheduler = new PerformanceNotificationScheduler(
                purchaseHistoryRepository, notificationService, clock);

        scheduler.sendScheduledNotifications();

        verify(notificationService).notifyWeeklySalesGrowth(2L);
        verify(notificationService).notifyWeeklySalesGrowth(3L);
    }

    @Test
    void skipsWeeklyGrowthOnOtherDays() {
        PurchaseHistoryRepository purchaseHistoryRepository =
                mock(PurchaseHistoryRepository.class);
        PerformanceNotificationService notificationService =
                mock(PerformanceNotificationService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-25T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        PerformanceNotificationScheduler scheduler = new PerformanceNotificationScheduler(
                purchaseHistoryRepository, notificationService, clock);

        scheduler.sendScheduledNotifications();

        verifyNoInteractions(purchaseHistoryRepository, notificationService);
    }
}
