package com.fuma.hiselectors.purchase.scheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.performance.notification.PerformanceNotificationService;
import com.fuma.hiselectors.purchase.service.PurchaseAutoConfirmationService;
import java.util.List;
import org.junit.jupiter.api.Test;

class PurchaseAutoConfirmationSchedulerTest {

    @Test
    void checksFirstRevenueAfterConfirmationTransactionReturns() {
        PurchaseAutoConfirmationService confirmationService =
                mock(PurchaseAutoConfirmationService.class);
        PerformanceNotificationService notificationService =
                mock(PerformanceNotificationService.class);
        when(confirmationService.confirmExpiredPurchases()).thenReturn(
                new PurchaseAutoConfirmationService.ConfirmationResult(3, List.of(4L, 7L)));
        PurchaseAutoConfirmationScheduler scheduler =
                new PurchaseAutoConfirmationScheduler(confirmationService, notificationService);

        scheduler.confirmExpiredPurchases();

        verify(notificationService).notifyFirstRevenue(4L);
        verify(notificationService).notifyFirstRevenue(7L);
    }
}
