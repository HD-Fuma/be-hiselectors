package com.fuma.hiselectors.content.scheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.content.service.ContentBatchService;
import com.fuma.hiselectors.content.service.ContentBatchService.ContentBatchResult;
import org.junit.jupiter.api.Test;

class ContentBatchSchedulerTest {

    @Test
    void runsContentBatch() {
        ContentBatchService service = mock(ContentBatchService.class);
        ContentBatchResult result = new ContentBatchResult(2, 5, true, true);
        when(service.run()).thenReturn(result);
        ContentBatchScheduler scheduler = new ContentBatchScheduler(service);

        scheduler.runContentBatch();

        verify(service).run();
    }
}
