package com.fuma.hiselectors.settlement.scheduler;

import com.fuma.hiselectors.settlement.service.SettlementBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementEstimateScheduler {

    private final SettlementBatchService settlementBatchService;

    @Scheduled(
            cron = "${settlement.estimate.cron:0 0 3 * * *}",
            zone = "${settlement.zone:Asia/Seoul}")
    public void calculateOpenActivityMonth() {
        SettlementBatchService.SettlementBatchResult result =
                settlementBatchService.calculateOpenActivityMonth();
        log.info(
                "당월까지 예상 정산 산정 배치 완료: throughActivityMonth={}, processed={}, skipped={}, failed={}",
                result.activityMonth(),
                result.processedCount(),
                result.skippedCount(),
                result.failedCount());
    }
}
