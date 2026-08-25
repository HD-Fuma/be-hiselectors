package com.fuma.hiselectors.settlement.task;

import com.fuma.hiselectors.settlement.service.SettlementRecalculationService;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SettlementRecalculationTaskFactory {

    private final SettlementRecalculationService service;

    public SettlementRecalculationTask create(
            YearMonth activityMonth, Long selectorsId, boolean force) {
        return new SettlementRecalculationTask(service, activityMonth, selectorsId, force);
    }
}
