package com.fuma.hiselectors.settlement.task;

import com.fuma.hiselectors.settlement.dto.SettlementRecalculationResponse;
import com.fuma.hiselectors.settlement.service.SettlementRecalculationService;
import com.fuma.hiselectors.taskrun.service.TaskExecutionContext;
import com.fuma.hiselectors.taskrun.service.TrackedTask;
import java.time.YearMonth;

public class SettlementRecalculationTask implements TrackedTask {

    private final SettlementRecalculationService service;
    private final YearMonth activityMonth;
    private final Long selectorsId;
    private final boolean force;

    public SettlementRecalculationTask(
            SettlementRecalculationService service,
            YearMonth activityMonth,
            Long selectorsId,
            boolean force) {
        this.service = service;
        this.activityMonth = activityMonth;
        this.selectorsId = selectorsId;
        this.force = force;
    }

    @Override
    public void execute(TaskExecutionContext context) {
        context.progress().start("RECALCULATE", null);
        SettlementRecalculationResponse result =
                service.recalculate(activityMonth, selectorsId, force);
        int total = Math.multiplyExact(result.selectorsCount(), result.activityMonthsCount());
        int succeeded = Math.addExact(
                Math.addExact(result.createdCount(), result.updatedCount()),
                result.finalizedCount());
        context.progress().start("RECALCULATE", total);
        context.progress().advance(succeeded, result.failedCount(), result.skippedCount());
    }
}
