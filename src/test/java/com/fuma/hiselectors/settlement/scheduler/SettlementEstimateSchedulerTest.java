package com.fuma.hiselectors.settlement.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fuma.hiselectors.settlement.task.SettlementEstimateTask;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import com.fuma.hiselectors.taskrun.service.TaskRunExecutionService;
import com.fuma.hiselectors.taskrun.service.TaskStartCommand;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.annotation.Scheduled;
import tools.jackson.databind.ObjectMapper;

class SettlementEstimateSchedulerTest {

    @Test
    void submitsScheduledEstimateWithItsMode() {
        TaskRunExecutionService taskRunExecutionService = mock(TaskRunExecutionService.class);
        SettlementEstimateTask task = mock(SettlementEstimateTask.class);
        SettlementEstimateScheduler scheduler =
                new SettlementEstimateScheduler(taskRunExecutionService, task, new ObjectMapper());

        scheduler.calculateOpenActivityMonth();

        ArgumentCaptor<TaskStartCommand> command = ArgumentCaptor.forClass(TaskStartCommand.class);
        verify(taskRunExecutionService).submit(command.capture(), org.mockito.Mockito.same(task));
        assertThat(command.getValue().taskType()).isEqualTo(TaskType.SETTLEMENT_CALCULATION);
        assertThat(command.getValue().triggerType()).isEqualTo(TriggerType.SCHEDULED);
        assertThat(command.getValue().startedByAdminId()).isNull();
        assertThat(command.getValue().idempotencyKey()).isNotNull();
        assertThat(command.getValue().businessPayload().get("mode").stringValue())
                .isEqualTo("ESTIMATE");
    }

    @Test
    void preservesEstimateSchedule() throws NoSuchMethodException {
        Method method = SettlementEstimateScheduler.class
                .getDeclaredMethod("calculateOpenActivityMonth");

        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("${settlement.estimate.cron:0 0 3 * * *}");
        assertThat(scheduled.zone()).isEqualTo("${settlement.zone:Asia/Seoul}");
    }
}
