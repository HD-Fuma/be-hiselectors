package com.fuma.hiselectors.content.task;

import com.fuma.hiselectors.content.service.ContentBatchService;
import com.fuma.hiselectors.content.service.ContentBatchService.ContentBatchResult;
import com.fuma.hiselectors.taskrun.service.TaskExecutionContext;
import com.fuma.hiselectors.taskrun.service.TrackedTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContentSyncTask implements TrackedTask {

    private final ContentBatchService contentBatchService;

    @Override
    public void execute(TaskExecutionContext context) {
        ContentBatchResult result = contentBatchService.run(context.progress());
        log.info(
                "콘텐츠 배치 완료: newContentCount={}, engagementCount={}, "
                        + "newContentSucceeded={}, storedContentSucceeded={}",
                result.newContentCount(),
                result.engagementCount(),
                result.newContentSucceeded(),
                result.storedContentSucceeded());
    }

}
