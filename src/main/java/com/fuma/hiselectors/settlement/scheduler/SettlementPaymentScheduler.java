package com.fuma.hiselectors.settlement.scheduler;

import com.fuma.hiselectors.settlement.dto.SettlementPaymentResponse;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.repository.SettlementHistoryRepository;
import com.fuma.hiselectors.settlement.service.SettlementPaymentService;
import com.fuma.hiselectors.settlement.service.SettlementSchedulePolicy;
import com.fuma.hiselectors.settlement.service.SettlementStatusNotificationService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementPaymentScheduler {

    private final SettlementPaymentService settlementPaymentService;
    private final SettlementHistoryRepository settlementHistoryRepository;
    private final SettlementStatusNotificationService settlementStatusNotificationService;
    private final SettlementSchedulePolicy settlementSchedulePolicy;
    private final Clock clock;

    @Scheduled(
            cron = "${settlement.payment.cron:0 0 0 * * *}",
            zone = "${settlement.zone:Asia/Seoul}")
    public void processCurrentPaymentMonth() {
        SettlementPaymentResponse result = settlementPaymentService
                .processCurrentPaymentMonth();
        log.info(
                "정산 지급 배치 완료: paymentMonth={}, latestEligibleActivityMonth={}, processed={}, settled={}, held={}, skipped={}, failed={}",
                result.paymentMonth(), result.latestEligibleActivityMonth(), result.processedCount(),
                result.settledCount(), result.heldCount(), result.skippedCount(),
                result.failedCount());
    }

    @Scheduled(
            cron = "${settlement.notification.upcoming-cron:0 0 9 * * *}",
            zone = "${settlement.zone:Asia/Seoul}")
    public void notifyUpcomingSettlements() {
        LocalDate today = LocalDate.now(clock);
        YearMonth activityMonth = YearMonth.from(today).minusMonths(2);
        LocalDate paymentDate = settlementSchedulePolicy.paymentDate(activityMonth);
        // 주말 보정까지 반영한 실제 지급일의 3일 전에만 안내한다.
        if (!today.plusDays(3).equals(paymentDate)) {
            return;
        }
        int activityYearMonth = activityMonth.getYear() * 100 + activityMonth.getMonthValue();
        settlementHistoryRepository
                .findAllByStatusAndActivityYearMonthAndSettlementAmountGreaterThan(
                        SettlementStatus.PAYMENT_PENDING, activityYearMonth, 0L)
                .forEach(history -> settlementStatusNotificationService.notifyUpcoming(
                        history.getId(), paymentDate));
    }
}
