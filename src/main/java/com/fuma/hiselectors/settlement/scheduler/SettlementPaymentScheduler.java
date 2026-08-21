package com.fuma.hiselectors.settlement.scheduler;

import com.fuma.hiselectors.settlement.dto.SettlementPaymentResponse;
import com.fuma.hiselectors.settlement.service.SettlementPaymentService;
import com.fuma.hiselectors.settlement.service.SettlementSchedulePolicy;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementPaymentScheduler {

    private final SettlementPaymentService settlementPaymentService;
    private final SettlementSchedulePolicy settlementSchedulePolicy;
    private final Clock clock;

    @Scheduled(
            cron = "${settlement.payment.cron:0 0 0 * * *}",
            zone = "${settlement.zone:Asia/Seoul}")
    public void processCurrentPaymentMonth() {
        if (!settlementSchedulePolicy.isPaymentDate(LocalDate.now(clock))) {
            return;
        }
        SettlementPaymentResponse result = settlementPaymentService
                .processCurrentPaymentMonth();
        log.info(
                "정산 지급 배치 완료: paymentMonth={}, latestEligibleActivityMonth={}, processed={}, settled={}, held={}, skipped={}, failed={}",
                result.paymentMonth(), result.latestEligibleActivityMonth(), result.processedCount(),
                result.settledCount(), result.heldCount(), result.skippedCount(),
                result.failedCount());
    }
}
