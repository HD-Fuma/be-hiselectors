package com.fuma.hiselectors.settlement.task;

import com.fuma.hiselectors.settlement.service.SettlementBatchService;
import com.fuma.hiselectors.settlement.service.SettlementBatchService.SettlementBatchResult;
import com.fuma.hiselectors.taskrun.service.TaskExecutionContext;
import com.fuma.hiselectors.taskrun.service.TrackedTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementEstimateTask implements TrackedTask {

    private final SettlementBatchService settlementBatchService;

    @Override
    public void execute(TaskExecutionContext context) {
        context.progress().start("ESTIMATE", null);
        SettlementBatchResult result =
                settlementBatchService.calculateOpenActivityMonth(context.progress());
        log.info(
                "당월까지 예상 정산 산정 배치 완료: throughActivityMonth={}, processed={}, skipped={}, failed={}",
                result.activityMonth(),
                result.processedCount(),
                result.skippedCount(),
                result.failedCount());
    }
}
