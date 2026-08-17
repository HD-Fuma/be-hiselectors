package com.fuma.hiselectors.settlement.scheduler;

import com.fuma.hiselectors.settlement.service.SettlementBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementFinalizationScheduler {

    private final SettlementBatchService settlementBatchService;

    @Scheduled(
            cron = "${settlement.finalization.cron:0 0 0 * * *}",
            zone = "${settlement.zone:Asia/Seoul}")
    public void finalizeOpenActivityMonth() {
        SettlementBatchService.SettlementBatchResult result =
                settlementBatchService.finalizeOpenActivityMonth();
        if (!result.finalized()) {
            return;
        }
        log.info("활동월 정산 확정 배치 완료: activityMonth={}, processed={}, skipped={}, failed={}",
                result.activityMonth(), result.processedCount(), result.skippedCount(),
                result.failedCount());
    }
}
