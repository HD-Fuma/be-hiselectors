package com.fuma.hiselectors.content.task;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fuma.hiselectors.content.service.ContentBatchService;
import com.fuma.hiselectors.content.service.ContentBatchService.ContentBatchResult;
import com.fuma.hiselectors.content.service.ContentBatchMode;
import com.fuma.hiselectors.inspection.task.ContentReportGenerationTask;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskRunStatus;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import com.fuma.hiselectors.taskrun.service.TaskExecutionContext;
import com.fuma.hiselectors.taskrun.service.TaskLease;
import com.fuma.hiselectors.taskrun.service.TaskProgressReporter;
import com.fuma.hiselectors.taskrun.service.TaskRunExecutionService;
import com.fuma.hiselectors.taskrun.service.TaskStartCommand;
import com.fuma.hiselectors.taskrun.service.TaskStartResult;
import com.fuma.hiselectors.taskrun.service.TaskTerminalContext;
import com.fuma.hiselectors.taskrun.service.TrackedTask;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

class ContentSyncTaskTest {

    private final ContentBatchService contentBatchService = mock(ContentBatchService.class);
    private final TaskRunExecutionService taskRunExecutionService =
            mock(TaskRunExecutionService.class);
    private final ContentReportGenerationTask contentReportGenerationTask =
            mock(ContentReportGenerationTask.class);
    private final Logger log = (Logger) LoggerFactory.getLogger(ContentSyncTask.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private ContentSyncTask task;

    @BeforeEach
    void setUp() {
        appender.start();
        log.addAppender(appender);
        task = new ContentSyncTask(
                contentBatchService,
                taskRunExecutionService,
                contentReportGenerationTask,
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        log.detachAppender(appender);
        appender.stop();
    }

    @Test
    void delegatesProgressReportingToContentBatchService() throws Exception {
        TaskProgressReporter progress = mock(TaskProgressReporter.class);
        when(contentBatchService.run(progress))
                .thenReturn(new ContentBatchResult(2, 3, true, false));

        task.execute(new TaskExecutionContext(mock(TaskLease.class), progress));

        verify(contentBatchService).run(progress);
        verifyNoInteractions(progress);
        verifyNoInteractions(taskRunExecutionService, contentReportGenerationTask);
    }

    @Test
    void fastModeTaskRunsScopedBatch() throws Exception {
        TaskProgressReporter progress = mock(TaskProgressReporter.class);
        when(contentBatchService.run(progress, ContentBatchMode.FAST))
                .thenReturn(new ContentBatchResult(1, 2, true, true));

        task.fastModeTask().execute(
                new TaskExecutionContext(mock(TaskLease.class), progress));

        verify(contentBatchService).run(progress, ContentBatchMode.FAST);
    }

    @ParameterizedTest
    @EnumSource(value = TaskRunStatus.class, names = {"SUCCEEDED", "PARTIAL_FAILED", "FAILED"})
    void submitsContentReportAfterEligibleTerminalStatuses(TaskRunStatus status) {
        UUID runId = UUID.randomUUID();

        task.afterTerminal(new TaskTerminalContext(runId, status));

        verify(taskRunExecutionService).submit(any(), same(contentReportGenerationTask));
    }

    @Test
    void skipsContentReportAfterStaleTerminalStatus() {
        task.afterTerminal(new TaskTerminalContext(UUID.randomUUID(), TaskRunStatus.STALE));

        verifyNoInteractions(taskRunExecutionService, contentReportGenerationTask);
    }

    @Test
    void childCommandUsesScheduledMetadataPayloadAndDeterministicParentKey() {
        UUID firstParent = UUID.fromString("94bc7ce2-9225-4232-bd2a-ac37b0fd62c9");
        UUID secondParent = UUID.fromString("c205f0b1-8b8a-4015-9db6-22951b55b81b");

        task.afterTerminal(terminal(firstParent));
        task.afterTerminal(terminal(firstParent));
        task.afterTerminal(terminal(secondParent));

        ArgumentCaptor<TaskStartCommand> commands =
                ArgumentCaptor.forClass(TaskStartCommand.class);
        verify(taskRunExecutionService, times(3))
                .submit(commands.capture(), same(contentReportGenerationTask));
        List<TaskStartCommand> values = commands.getAllValues();
        TaskStartCommand first = values.getFirst();
        assertThat(first.taskType()).isEqualTo(TaskType.CONTENT_REPORT_GENERATION);
        assertThat(first.triggerType()).isEqualTo(TriggerType.SCHEDULED);
        assertThat(first.startedByAdminId()).isNull();
        assertThat(first.businessPayload().get("sourceContentSyncRunId").stringValue())
                .isEqualTo(firstParent.toString());
        assertThat(first.idempotencyKey()).isEqualTo(key(firstParent));
        assertThat(values.get(1).idempotencyKey()).isEqualTo(first.idempotencyKey());
        assertThat(values.get(2).idempotencyKey()).isEqualTo(key(secondParent));
        assertThat(values.get(2).idempotencyKey()).isNotEqualTo(first.idempotencyKey());
    }

    @Test
    void fastModeSubmitsScopedReportTaskWithFingerprintPayload() {
        UUID sourceRunId = UUID.fromString("94bc7ce2-9225-4232-bd2a-ac37b0fd62c9");
        TrackedTask fastReportTask = mock(TrackedTask.class);
        when(contentReportGenerationTask.fastModeTask()).thenReturn(fastReportTask);

        task.fastModeTask().afterTerminal(terminal(sourceRunId));

        ArgumentCaptor<TaskStartCommand> command =
                ArgumentCaptor.forClass(TaskStartCommand.class);
        verify(taskRunExecutionService).submit(command.capture(), same(fastReportTask));
        assertThat(command.getValue().businessPayload().get("sourceContentSyncRunId").stringValue())
                .isEqualTo(sourceRunId.toString());
        assertThat(command.getValue().businessPayload().get("fastMode").booleanValue()).isTrue();
    }

    @Test
    void activeConflictLogsSourceAndActiveReportRunIdsAndReturnsNormally() {
        UUID sourceRunId = UUID.fromString("2fd256a3-e7b4-4a54-82f0-717f4ed56d96");
        UUID activeReportRunId = UUID.fromString("932ef79b-7304-4bb1-8da4-af49b676f21f");
        TaskRun activeRun = mock(TaskRun.class);
        when(activeRun.getRunId()).thenReturn(activeReportRunId);
        when(taskRunExecutionService.submit(any(), same(contentReportGenerationTask)))
                .thenReturn(new TaskStartResult.ActiveConflict(activeRun));

        task.afterTerminal(terminal(sourceRunId));

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .singleElement()
                .asString()
                .contains(
                        "sourceContentSyncRunId=" + sourceRunId,
                        "activeReportRunId=" + activeReportRunId);
    }

    private TaskTerminalContext terminal(UUID runId) {
        return new TaskTerminalContext(runId, TaskRunStatus.SUCCEEDED);
    }

    private UUID key(UUID sourceRunId) {
        return UUID.nameUUIDFromBytes(
                ("content-report-after-content-sync:" + sourceRunId).getBytes(UTF_8));
    }
}
