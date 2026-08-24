package com.fuma.hiselectors.settlement.scheduler;

import com.fuma.hiselectors.settlement.task.SettlementFinalizationTask;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import com.fuma.hiselectors.taskrun.service.TaskRunExecutionService;
import com.fuma.hiselectors.taskrun.service.TaskStartCommand;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class SettlementFinalizationScheduler {

    private final TaskRunExecutionService taskRunExecutionService;
    private final SettlementFinalizationTask task;
    private final ObjectMapper objectMapper;

    @Scheduled(
            cron = "${settlement.finalization.cron:0 0 0 * * *}",
            zone = "${settlement.zone:Asia/Seoul}")
    public void finalizeOpenActivityMonth() {
        taskRunExecutionService.submit(
                new TaskStartCommand(
                        TaskType.SETTLEMENT_CALCULATION,
                        TriggerType.SCHEDULED,
                        null,
                        UUID.randomUUID(),
                        objectMapper.createObjectNode().put("mode", "FINALIZE")),
                task);
    }
}
