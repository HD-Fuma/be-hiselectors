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
    public void calculatePreviousMonth() {
        SettlementBatchService.SettlementBatchResult result =
                settlementBatchService.calculatePreviousMonth();
        log.info(
                "전월 정산 계산 배치 완료: settlementMonth={}, processed={}, skipped={}, failed={}, finalized={}",
                result.settlementMonth(),
                result.processedCount(),
                result.skippedCount(),
                result.failedCount(),
                result.finalized());
    }
}
