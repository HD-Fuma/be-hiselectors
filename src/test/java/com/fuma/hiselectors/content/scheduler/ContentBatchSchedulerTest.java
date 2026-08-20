package com.fuma.hiselectors.content.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.content.service.ContentBatchService;
import com.fuma.hiselectors.content.service.ContentBatchService.ContentBatchResult;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

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

    @Test
    void schedulesContentBatchWithConfiguredCron() throws NoSuchMethodException {
        Method method = ContentBatchScheduler.class.getDeclaredMethod("runContentBatch");

        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("${content.batch.cron:-}");
        assertThat(scheduled.zone()).isEqualTo("${content.batch.zone:Asia/Seoul}");
    }
}
