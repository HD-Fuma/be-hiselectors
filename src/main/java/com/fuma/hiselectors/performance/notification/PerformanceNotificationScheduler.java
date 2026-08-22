package com.fuma.hiselectors.performance.notification;

import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.purchase.repository.PurchaseHistoryRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PerformanceNotificationScheduler {

    private final PurchaseHistoryRepository purchaseHistoryRepository;
    private final PerformanceNotificationService performanceNotificationService;
    private final Clock clock;

    @Scheduled(
            cron = "${performance.notification.daily-cron:0 0 9 * * *}",
            zone = "${performance.notification.zone:Asia/Seoul}")
    public void sendScheduledNotifications() {
        LocalDate today = LocalDate.now(clock);
        // 완료된 월~일 매출은 다음 월요일에 한 번만 비교한다.
        if (today.getDayOfWeek() != DayOfWeek.MONDAY) {
            return;
        }
        LocalDateTime currentWeekStart = today.atStartOfDay();
        LocalDateTime lastWeekStart = today.minusWeeks(1).atStartOfDay();
        purchaseHistoryRepository.findDistinctSelectorsIdsByStatusAndConfirmedAtBetween(
                        PurchaseStatus.PURCHASE_CONFIRMED,
                        lastWeekStart, currentWeekStart)
                .forEach(performanceNotificationService::notifyWeeklySalesGrowth);
    }
}
