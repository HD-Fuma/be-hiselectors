package com.fuma.hiselectors.content.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fuma.hiselectors.content.task.ContentSyncTask;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import com.fuma.hiselectors.taskrun.service.TaskRunExecutionService;
import com.fuma.hiselectors.taskrun.service.TaskStartCommand;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.annotation.Scheduled;
import tools.jackson.databind.ObjectMapper;

class ContentBatchSchedulerTest {

    @Test
    void submitsScheduledContentSync() {
        TaskRunExecutionService taskRunExecutionService = mock(TaskRunExecutionService.class);
        ContentSyncTask task = mock(ContentSyncTask.class);
        ContentBatchScheduler scheduler =
                new ContentBatchScheduler(taskRunExecutionService, task, new ObjectMapper());

        scheduler.runContentBatch();

        ArgumentCaptor<TaskStartCommand> command = ArgumentCaptor.forClass(TaskStartCommand.class);
        verify(taskRunExecutionService).submit(command.capture(), org.mockito.Mockito.same(task));
        assertThat(command.getValue().taskType()).isEqualTo(TaskType.CONTENT_SYNC);
        assertThat(command.getValue().triggerType()).isEqualTo(TriggerType.SCHEDULED);
        assertThat(command.getValue().startedByAdminId()).isNull();
        assertThat(command.getValue().idempotencyKey()).isNotNull();
        assertThat(command.getValue().businessPayload().isEmpty()).isTrue();
    }

    @Test
    void schedulesContentBatchWithConfiguredCron() throws NoSuchMethodException {
        Method method = ContentBatchScheduler.class.getDeclaredMethod("runContentBatch");

        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("${content.batch.cron:-}");
        assertThat(scheduled.zone()).isEqualTo("${content.batch.zone:Asia/Seoul}");
    }
}
