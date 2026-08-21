package com.fuma.hiselectors.content.scheduler;

import com.fuma.hiselectors.content.service.ContentBatchService;
import com.fuma.hiselectors.content.service.ContentBatchService.ContentBatchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContentBatchScheduler {

    private final ContentBatchService contentBatchService;

    @Scheduled(
            cron = "${content.batch.cron:-}",
            zone = "${content.batch.zone:Asia/Seoul}")
    public void runContentBatch() {
        ContentBatchResult result = contentBatchService.run();
        log.info(
                "콘텐츠 배치 완료: newContentCount={}, engagementCount={}, "
                        + "newContentSucceeded={}, storedContentSucceeded={}",
                result.newContentCount(),
                result.engagementCount(),
                result.newContentSucceeded(),
                result.storedContentSucceeded());
    }
}
