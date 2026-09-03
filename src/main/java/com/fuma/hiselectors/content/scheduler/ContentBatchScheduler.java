package com.fuma.hiselectors.content.scheduler;

import com.fuma.hiselectors.content.task.ContentSyncTask;
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
public class ContentBatchScheduler {

    private final TaskRunExecutionService taskRunExecutionService;
    private final ContentSyncTask contentSyncTask;
    private final ObjectMapper objectMapper;

    @Scheduled(
            cron = "${content.batch.cron:-}",
            zone = "${content.batch.zone:Asia/Seoul}")
    public void runContentBatch() {
        taskRunExecutionService.submit(
                new TaskStartCommand(
                        TaskType.CONTENT_SYNC,
                        TriggerType.SCHEDULED,
                        null,
                        UUID.randomUUID(),
                        objectMapper.createObjectNode()),
                contentSyncTask);
    }
}
