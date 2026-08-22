package com.fuma.hiselectors.purchase.scheduler;

import com.fuma.hiselectors.performance.notification.PerformanceNotificationService;
import com.fuma.hiselectors.purchase.service.PurchaseAutoConfirmationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseAutoConfirmationScheduler {

    private final PurchaseAutoConfirmationService purchaseAutoConfirmationService;
    private final PerformanceNotificationService performanceNotificationService;

    @Scheduled(
            cron = "${purchase.auto-confirm.cron:0 5 0 * * *}",
            zone = "${settlement.zone:Asia/Seoul}")
    public void confirmExpiredPurchases() {
        PurchaseAutoConfirmationService.ConfirmationResult result =
                purchaseAutoConfirmationService.confirmExpiredPurchases();
        result.selectorsIds().forEach(selectorsId -> {
            performanceNotificationService.notifyFirstRevenue(selectorsId);
            performanceNotificationService.notifySalesMilestone(selectorsId);
        });
        log.info("구매 자동확정 배치 완료: confirmedCount={}", result.confirmedCount());
    }
}
