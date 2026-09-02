package com.fuma.hiselectors.taskrun.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.admin.repository.AdminRepository;
import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskRunStatus;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import com.fuma.hiselectors.taskrun.service.TaskRunExecutionService;
import com.fuma.hiselectors.taskrun.service.TaskRunTaskResolver;
import com.fuma.hiselectors.taskrun.service.TaskStartCommand;
import com.fuma.hiselectors.taskrun.service.TaskStartResult;
import com.fuma.hiselectors.taskrun.service.TrackedTask;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

class TaskQueueRetryControllerContractTest {

    private static final Instant NOW = Instant.parse("2026-08-31T00:00:00Z");
    private static final UUID RETRY_KEY = UUID.fromString("c946b463-163d-454a-963a-f845dcdcf89e");
    private static final String PAYLOAD = "{\"source\":\"youtube-category\",\"categoryId\":88}";

    private final TaskQueueState state = mock(TaskQueueState.class);
    private final TaskRunTaskResolver resolver = mock(TaskRunTaskResolver.class);
    private final TaskRunExecutionService execution = mock(TaskRunExecutionService.class);
    private final AdminRepository adminRepository = mock(AdminRepository.class);
    private final TrackedTask task = mock(TrackedTask.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        Admin admin = mock(Admin.class);
        when(admin.getId()).thenReturn(7L);
        when(admin.getName()).thenReturn("현재 관리자");
        when(adminRepository.findByLoginId("current-admin")).thenReturn(Optional.of(admin));
        when(resolver.automaticRetrySafe(any())).thenCallRealMethod();
        when(resolver.resolve(any())).thenReturn(task);
        mvc = MockMvcBuilders.standaloneSetup(new TaskQueueRetryController(
                        state, resolver, execution, adminRepository, mapper))
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResultAdvice())
                .build();
    }

    @Test
    void preservesEntireOriginalScopeAndHistoryButUsesCurrentAdminAndNewKey() throws Exception {
        TaskRun source = source(TaskType.CREATOR_SYNC, TaskRunStatus.PARTIAL_FAILED, true, PAYLOAD);
        TaskRun retried = retried(source.getTaskType());
        UUID originalKey = source.getIdempotencyKey();
        Instant originalFinishedAt = source.getFinishedAt();
        when(state.get(source.getRunId())).thenReturn(source);
        when(execution.submit(any(), same(task))).thenReturn(new TaskStartResult.Created(retried));

        mvc.perform(request(source))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.runId").value(retried.getRunId().toString()))
                .andExpect(jsonPath("$.data.triggerType").value("ADMIN_TRIGGERED"))
                .andExpect(jsonPath("$.data.startedBy.adminId").value(7))
                .andExpect(jsonPath("$.data.startedBy.name").value("현재 관리자"))
                .andExpect(jsonPath("$.data.businessPayload").doesNotExist());

        ArgumentCaptor<TaskStartCommand> command = ArgumentCaptor.forClass(TaskStartCommand.class);
        verify(execution).submit(command.capture(), same(task));
        assertThat(command.getValue().taskType()).isEqualTo(TaskType.CREATOR_SYNC);
        assertThat(command.getValue().businessPayload()).isEqualTo(mapper.readTree(PAYLOAD));
        assertThat(command.getValue().triggerType()).isEqualTo(TriggerType.ADMIN_TRIGGERED);
        assertThat(command.getValue().startedByAdminId()).isEqualTo(7L);
        assertThat(command.getValue().idempotencyKey()).isEqualTo(RETRY_KEY).isNotEqualTo(originalKey);
        verify(resolver).resolve(source);
        verify(adminRepository).findByLoginId("current-admin");
        assertThat(source.getStatus()).isEqualTo(TaskRunStatus.PARTIAL_FAILED);
        assertThat(source.getTriggerType()).isEqualTo(TriggerType.SCHEDULED);
        assertThat(source.getStartedByAdminId()).isNull();
        assertThat(source.getIdempotencyKey()).isEqualTo(originalKey);
        assertThat(source.getBusinessPayload()).isEqualTo(PAYLOAD);
        assertThat(source.getProcessedCount()).isEqualTo(2);
        assertThat(source.getSucceededCount()).isEqualTo(1);
        assertThat(source.getFailedCount()).isEqualTo(1);
        assertThat(source.getFinishedAt()).isEqualTo(originalFinishedAt);
    }

    @ParameterizedTest
    @EnumSource(value = TaskRunStatus.class, names = {"FAILED", "PARTIAL_FAILED", "STALE"})
    void acceptsOnlyFailedTerminalStates(TaskRunStatus status) throws Exception {
        TaskRun source = source(TaskType.CONTENT_SYNC, status, true, "{}");
        when(state.get(source.getRunId())).thenReturn(source);
        when(execution.submit(any(), any())).thenReturn(new TaskStartResult.Created(retried(source.getTaskType())));

        mvc.perform(request(source)).andExpect(status().isAccepted());
        assertThat(source.getStatus()).isEqualTo(status);
    }

    @ParameterizedTest
    @EnumSource(value = TaskRunStatus.class, names = {"QUEUED", "RUNNING", "SUCCEEDED"})
    void refusesActiveOrCompletedRuns(TaskRunStatus status) throws Exception {
        TaskRun source = source(TaskType.CONTENT_SYNC, status, true, "{}");
        when(state.get(source.getRunId())).thenReturn(source);

        mvc.perform(request(source)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TASK_RUN_TRANSITION"));
        verifyNoInteractions(execution);
    }

    @ParameterizedTest
    @EnumSource(value = TaskType.class, names = {"SETTLEMENT_CALCULATION", "PROPOSAL_EMAIL_SEND",
            "KAKAO_MESSAGE_SEND", "APPLICATION_REPORT_GENERATION", "CONTENT_REPORT_GENERATION"})
    void refusesTasksRequiringDomainRecovery(TaskType type) throws Exception {
        TaskRun source = source(type, TaskRunStatus.FAILED, true, "{}");
        when(state.get(source.getRunId())).thenReturn(source);

        mvc.perform(request(source)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TASK_RUN_TRANSITION"));
        verify(resolver, never()).resolve(any());
        verifyNoInteractions(execution);
    }

    @Test
    void refusesLegacyRunWithoutDurableCommand() throws Exception {
        TaskRun source = source(TaskType.CONTENT_SYNC, TaskRunStatus.FAILED, false, null);
        when(state.get(source.getRunId())).thenReturn(source);

        mvc.perform(request(source)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TASK_RUN_TRANSITION"));
        verifyNoInteractions(execution, resolver);
    }

    @Test
    void refusesOriginalIdempotencyKeySoItCannotReplayTheFailedOriginal() throws Exception {
        TaskRun source = source(TaskType.CONTENT_SYNC, TaskRunStatus.FAILED, true, "{}");
        when(state.get(source.getRunId())).thenReturn(source);

        mvc.perform(post("/api/admin/task-runs/{runId}/retry", source.getRunId())
                        .header("Idempotency-Key", source.getIdempotencyKey())
                        .principal(() -> "current-admin"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TASK_RUN_TRANSITION"));
        verifyNoInteractions(execution);
    }

    @Test
    void delegatesSameNewKeyToExistingIdempotencyAndReturnsTheSameAcceptedRun() throws Exception {
        TaskRun source = source(TaskType.CONTENT_SYNC, TaskRunStatus.FAILED, true, "{}");
        TaskRun retried = retried(source.getTaskType());
        when(state.get(source.getRunId())).thenReturn(source);
        when(execution.submit(any(), any())).thenReturn(
                new TaskStartResult.Created(retried), new TaskStartResult.Replayed(retried));

        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(request(source)).andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.data.runId").value(retried.getRunId().toString()));
        }

        ArgumentCaptor<TaskStartCommand> commands = ArgumentCaptor.forClass(TaskStartCommand.class);
        verify(execution, times(2)).submit(commands.capture(), same(task));
        assertThat(commands.getAllValues()).allSatisfy(command -> {
            assertThat(command.idempotencyKey()).isEqualTo(RETRY_KEY);
            assertThat(command.businessPayload()).isEqualTo(mapper.readTree("{}"));
        });
        assertThat(source.getStatus()).isEqualTo(TaskRunStatus.FAILED);
    }

    @Test
    void retainsExistingActiveConflictResponse() throws Exception {
        TaskRun source = source(TaskType.CONTENT_SYNC, TaskRunStatus.FAILED, true, "{}");
        when(state.get(source.getRunId())).thenReturn(source);
        when(execution.submit(any(), any()))
                .thenReturn(new TaskStartResult.ActiveConflict(retried(source.getTaskType())));

        mvc.perform(request(source)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_ALREADY_RUNNING"));
    }

    @Test
    void requiresPrincipalAndRechecksCurrentAdminBeforeReadingSource() throws Exception {
        mvc.perform(post("/api/admin/task-runs/{runId}/retry", UUID.randomUUID())
                        .header("Idempotency-Key", RETRY_KEY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(adminRepository, state, execution);

        when(adminRepository.findByLoginId("deleted-admin")).thenReturn(Optional.empty());
        mvc.perform(post("/api/admin/task-runs/{runId}/retry", UUID.randomUUID())
                        .header("Idempotency-Key", RETRY_KEY)
                        .principal(() -> "deleted-admin"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADMIN_NOT_FOUND"));
        verifyNoInteractions(state, execution);
    }

    @Test
    void requiresUuidIdempotencyHeader() throws Exception {
        mvc.perform(post("/api/admin/task-runs/{runId}/retry", UUID.randomUUID())
                        .principal(() -> "current-admin"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/task-runs/{runId}/retry", UUID.randomUUID())
                        .header("Idempotency-Key", "not-a-uuid")
                        .principal(() -> "current-admin"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(state, execution);
    }

    @Test
    void invalidStoredJsonIsRejectedWithoutLeakingPayload() throws Exception {
        TaskRun source = source(TaskType.CREATOR_SYNC, TaskRunStatus.FAILED, true, "{private-content-invalid");
        when(state.get(source.getRunId())).thenReturn(source);

        String response = mvc.perform(request(source)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TASK_RUN_TRANSITION"))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("private-content-invalid");
        verifyNoInteractions(execution);
    }

    @Test
    void resolverRejectionCannotFallBackToABroaderTask() throws Exception {
        TaskRun source = source(TaskType.CREATOR_SYNC, TaskRunStatus.FAILED, true, PAYLOAD);
        when(state.get(source.getRunId())).thenReturn(source);
        when(resolver.resolve(source)).thenThrow(new IllegalArgumentException("private invalid scope"));

        String response = mvc.perform(request(source)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TASK_RUN_TRANSITION"))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("private invalid scope");
        verifyNoInteractions(execution);
    }

    @Test
    void unavailableUnlessQueuePublishingIsExplicitlyEnabled() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(TaskQueueRetryController.class);
        runner.run(context -> assertThat(context).doesNotHaveBean(TaskQueueRetryController.class));
        runner.withPropertyValues("task-queue.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(TaskQueueRetryController.class));
    }

    private MockHttpServletRequestBuilder request(TaskRun source) {
        return post("/api/admin/task-runs/{runId}/retry", source.getRunId())
                .header("Idempotency-Key", RETRY_KEY).principal(() -> "current-admin");
    }

    private TaskRun retried(TaskType type) {
        return TaskRun.queued(type, TriggerType.ADMIN_TRIGGERED, 7L, RETRY_KEY,
                "retry-fingerprint", type.name(), NOW);
    }

    private TaskRun source(TaskType type, TaskRunStatus status, boolean queueManaged, String payload) {
        TaskRun run = TaskRun.queued(type, TriggerType.SCHEDULED, null, UUID.randomUUID(),
                "original-fingerprint", type.name(), NOW);
        if (queueManaged) {
            run.enableQueue(payload, NOW);
        }
        if (status != TaskRunStatus.QUEUED) {
            run.markRunning(UUID.randomUUID(), NOW);
        }
        switch (status) {
            case FAILED -> run.fail("TEST_FAILURE", "safe summary", NOW.plusSeconds(10));
            case STALE -> run.markStale(UUID.randomUUID(), true, NOW.plusSeconds(10));
            case PARTIAL_FAILED -> {
                run.addCounts(1, 1, 0, NOW);
                run.complete(NOW.plusSeconds(10));
            }
            case SUCCEEDED -> run.complete(NOW.plusSeconds(10));
            default -> { }
        }
        return run;
    }
}
