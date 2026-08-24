package com.fuma.hiselectors.content.task;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.content.service.ContentBatchService;
import com.fuma.hiselectors.content.service.ContentBatchService.ContentBatchResult;
import com.fuma.hiselectors.taskrun.service.TaskExecutionContext;
import com.fuma.hiselectors.taskrun.service.TaskLease;
import com.fuma.hiselectors.taskrun.service.TaskProgressReporter;
import org.junit.jupiter.api.Test;

class ContentSyncTaskTest {

    @Test
    void delegatesProgressReportingToContentBatchService() throws Exception {
        ContentBatchService service = mock(ContentBatchService.class);
        TaskProgressReporter progress = mock(TaskProgressReporter.class);
        when(service.run(progress)).thenReturn(new ContentBatchResult(2, 3, true, false));
        ContentSyncTask task = new ContentSyncTask(service);

        task.execute(new TaskExecutionContext(mock(TaskLease.class), progress));

        verify(service).run(progress);
        verifyNoInteractions(progress);
    }
}
