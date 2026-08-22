package com.fuma.hiselectors.performance.notification;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.purchase.repository.PurchaseHistoryRepository;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
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
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-24T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        when(purchaseHistoryRepository.findDistinctSelectorsIdsByStatusAndConfirmedAtBetween(
                PurchaseStatus.PURCHASE_CONFIRMED,
                LocalDateTime.of(2026, 8, 17, 0, 0),
                LocalDateTime.of(2026, 8, 24, 0, 0)))
                .thenReturn(List.of(2L, 3L));
        PerformanceNotificationScheduler scheduler = new PerformanceNotificationScheduler(
                purchaseHistoryRepository, selectorsRepository, notificationService, clock);

        scheduler.sendScheduledNotifications();

        verify(notificationService).notifyWeeklySalesGrowth(2L);
        verify(notificationService).notifyWeeklySalesGrowth(3L);
    }

    @Test
    void skipsWeeklyAndMidMonthChecksOnOtherDays() {
        PurchaseHistoryRepository purchaseHistoryRepository =
                mock(PurchaseHistoryRepository.class);
        PerformanceNotificationService notificationService =
                mock(PerformanceNotificationService.class);
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-25T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        PerformanceNotificationScheduler scheduler = new PerformanceNotificationScheduler(
                purchaseHistoryRepository, selectorsRepository, notificationService, clock);

        scheduler.sendScheduledNotifications();

        verify(selectorsRepository).findActiveIdsWithoutViewsAfterActivityStarted(
                Selectors.ACTIVE_ROLE,
                LocalDateTime.of(2026, 8, 25, 9, 0),
                LocalDateTime.of(2026, 8, 18, 9, 0));
        verifyNoInteractions(purchaseHistoryRepository, notificationService);
    }

    @Test
    void checksMidMonthActivityForSelectorsWithoutPurchasesOnSixteenth() {
        PurchaseHistoryRepository purchaseHistoryRepository =
                mock(PurchaseHistoryRepository.class);
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        PerformanceNotificationService notificationService =
                mock(PerformanceNotificationService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-09-16T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        when(selectorsRepository.findActiveIdsWithoutPurchasesBetween(
                Selectors.ACTIVE_ROLE,
                LocalDateTime.of(2026, 9, 1, 0, 0),
                LocalDateTime.of(2026, 9, 16, 0, 0)))
                .thenReturn(List.of(2L, 3L));
        when(selectorsRepository.findActiveIdsWithoutViewsAfterActivityStarted(
                Selectors.ACTIVE_ROLE,
                LocalDateTime.of(2026, 9, 16, 9, 0),
                LocalDateTime.of(2026, 9, 9, 9, 0)))
                .thenReturn(List.of(2L, 4L));
        PerformanceNotificationScheduler scheduler = new PerformanceNotificationScheduler(
                purchaseHistoryRepository, selectorsRepository, notificationService, clock);

        scheduler.sendScheduledNotifications();

        verify(notificationService).notifyMidMonthActivity(2L);
        verify(notificationService).notifyMidMonthActivity(3L);
        verify(notificationService).notifyNoPageViews(4L);
    }

    @Test
    void checksNoPageViewsEveryDay() {
        PurchaseHistoryRepository purchaseHistoryRepository =
                mock(PurchaseHistoryRepository.class);
        SelectorsRepository selectorsRepository = mock(SelectorsRepository.class);
        PerformanceNotificationService notificationService =
                mock(PerformanceNotificationService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-09-17T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        when(selectorsRepository.findActiveIdsWithoutViewsAfterActivityStarted(
                Selectors.ACTIVE_ROLE,
                LocalDateTime.of(2026, 9, 17, 9, 0),
                LocalDateTime.of(2026, 9, 10, 9, 0)))
                .thenReturn(List.of(5L));
        PerformanceNotificationScheduler scheduler = new PerformanceNotificationScheduler(
                purchaseHistoryRepository, selectorsRepository, notificationService, clock);

        scheduler.sendScheduledNotifications();

        verify(notificationService).notifyNoPageViews(5L);
    }
}
