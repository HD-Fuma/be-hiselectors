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
        // 구매확정 트랜잭션이 끝난 뒤 셀렉터스별 성과를 한 번에 확인한다.
        result.selectorsIds().forEach(performanceNotificationService::notifyConfirmedPerformance);
        log.info("구매 자동확정 배치 완료: confirmedCount={}", result.confirmedCount());
    }
}
