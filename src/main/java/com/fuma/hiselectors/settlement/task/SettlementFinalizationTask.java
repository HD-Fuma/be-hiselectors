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
public class SettlementFinalizationTask implements TrackedTask {

    private final SettlementBatchService settlementBatchService;

    @Override
    public void execute(TaskExecutionContext context) {
        context.progress().start("FINALIZE", null);
        SettlementBatchResult result =
                settlementBatchService.finalizeOpenActivityMonth(context.progress());
        if (!result.finalized()) {
            return;
        }
        log.info("활동월 정산 확정 배치 완료: activityMonth={}, processed={}, skipped={}, failed={}",
                result.activityMonth(), result.processedCount(), result.skippedCount(),
                result.failedCount());
    }
}
