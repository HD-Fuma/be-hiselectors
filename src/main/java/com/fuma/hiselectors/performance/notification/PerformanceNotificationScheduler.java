package com.fuma.hiselectors.performance.notification;

import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.purchase.repository.PurchaseHistoryRepository;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PerformanceNotificationScheduler {

    private final PurchaseHistoryRepository purchaseHistoryRepository;
    private final SelectorsRepository selectorsRepository;
    private final PerformanceNotificationService performanceNotificationService;
    private final Clock clock;

    @Scheduled(
            cron = "${performance.notification.daily-cron:0 0 9 * * *}",
            zone = "${performance.notification.zone:Asia/Seoul}")
    public void sendScheduledNotifications() {
        LocalDate today = LocalDate.now(clock);
        // 완료된 월~일 매출은 다음 월요일에 한 번만 비교한다.
        if (today.getDayOfWeek() == DayOfWeek.MONDAY) {
            sendWeeklySalesGrowth(today);
        }
        if (today.getDayOfMonth() == 16) {
            sendMidMonthActivity(today);
        }
    }

    private void sendWeeklySalesGrowth(LocalDate today) {
        purchaseHistoryRepository.findDistinctSelectorsIdsByStatusAndConfirmedAtBetween(
                        PurchaseStatus.PURCHASE_CONFIRMED,
                        today.minusWeeks(1).atStartOfDay(), today.atStartOfDay())
                .forEach(performanceNotificationService::notifyWeeklySalesGrowth);
    }

    private void sendMidMonthActivity(LocalDate today) {
        selectorsRepository.findActiveIdsWithoutPurchasesBetween(
                        Selectors.ACTIVE_ROLE,
                        today.withDayOfMonth(1).atStartOfDay(), today.atStartOfDay())
                .forEach(performanceNotificationService::notifyMidMonthActivity);
    }
}
