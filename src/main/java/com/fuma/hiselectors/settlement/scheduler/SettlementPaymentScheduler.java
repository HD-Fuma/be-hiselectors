package com.fuma.hiselectors.settlement.scheduler;

import com.fuma.hiselectors.settlement.dto.SettlementPaymentResponse;
import com.fuma.hiselectors.settlement.service.SettlementPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementPaymentScheduler {

    private final SettlementPaymentService settlementPaymentService;

    @Scheduled(
            cron = "${settlement.payment.cron:0 0 0 20 * *}",
            zone = "${settlement.zone:Asia/Seoul}")
    public void processPreviousPreviousMonth() {
        SettlementPaymentResponse result = settlementPaymentService
                .processPreviousPreviousMonth();
        log.info(
                "정산 지급 배치 완료: targetSettlementMonth={}, processed={}, settled={}, held={}, skipped={}, failed={}",
                result.targetSettlementMonth(), result.processedCount(),
                result.settledCount(), result.heldCount(), result.skippedCount(),
                result.failedCount());
    }
}
