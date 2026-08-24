package com.fuma.hiselectors.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.admin.repository.AdminRepository;
import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.content.task.ContentSyncTask;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import com.fuma.hiselectors.taskrun.service.TaskRunExecutionService;
import com.fuma.hiselectors.taskrun.service.TaskStartCommand;
import com.fuma.hiselectors.taskrun.service.TaskStartResult;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

class ContentBatchAdminControllerTest {

    private static final UUID IDEMPOTENCY_KEY =
            UUID.fromString("b3eb8cb5-b449-4f93-aa40-29d71428f6db");

    private TaskRunExecutionService taskRunExecutionService;
    private ContentSyncTask contentSyncTask;
    private AdminRepository adminRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        taskRunExecutionService = mock(TaskRunExecutionService.class);
        contentSyncTask = mock(ContentSyncTask.class);
        adminRepository = mock(AdminRepository.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ContentBatchAdminController(
                        taskRunExecutionService, contentSyncTask, adminRepository, new ObjectMapper()))
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResultAdvice())
                .build();
    }

    @Test
    void startsAdminContentSyncAsAcceptedAndTracksAdminAndHeader() throws Exception {
        Admin admin = mock(Admin.class);
        when(admin.getId()).thenReturn(7L);
        when(admin.getName()).thenReturn("관리자");
        when(adminRepository.findByLoginId("admin-login")).thenReturn(Optional.of(admin));
        when(taskRunExecutionService.submit(any(), any())).thenReturn(new TaskStartResult.Created(run(7L)));

        mockMvc.perform(post("/api/admin/content-batch/run")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .principal(() -> "admin-login"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.taskType").value("CONTENT_SYNC"))
                .andExpect(jsonPath("$.data.triggerType").value("ADMIN_TRIGGERED"))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.startedBy.adminId").value(7))
                .andExpect(jsonPath("$.data.startedBy.name").value("관리자"));

        ArgumentCaptor<TaskStartCommand> command = ArgumentCaptor.forClass(TaskStartCommand.class);
        verify(taskRunExecutionService).submit(command.capture(), org.mockito.Mockito.same(contentSyncTask));
        assertThat(command.getValue().taskType()).isEqualTo(TaskType.CONTENT_SYNC);
        assertThat(command.getValue().triggerType()).isEqualTo(TriggerType.ADMIN_TRIGGERED);
        assertThat(command.getValue().startedByAdminId()).isEqualTo(7L);
        assertThat(command.getValue().idempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(command.getValue().businessPayload().isObject()).isTrue();
        assertThat(command.getValue().businessPayload().isEmpty()).isTrue();
    }

    @Test
    void returnsAcceptedForIdempotentReplay() throws Exception {
        Admin admin = mock(Admin.class);
        when(admin.getId()).thenReturn(7L);
        when(admin.getName()).thenReturn("관리자");
        when(adminRepository.findByLoginId("admin-login")).thenReturn(Optional.of(admin));
        when(taskRunExecutionService.submit(any(), any())).thenReturn(new TaskStartResult.Replayed(run(7L)));

        mockMvc.perform(post("/api/admin/content-batch/run")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .principal(() -> "admin-login"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
    }

    @Test
    void returnsStableConflictWhenContentSyncIsAlreadyActive() throws Exception {
        Admin admin = mock(Admin.class);
        when(admin.getId()).thenReturn(7L);
        when(adminRepository.findByLoginId("admin-login")).thenReturn(Optional.of(admin));
        when(taskRunExecutionService.submit(any(), any()))
                .thenReturn(new TaskStartResult.ActiveConflict(run(8L)));

        mockMvc.perform(post("/api/admin/content-batch/run")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .principal(() -> "admin-login"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TASK_ALREADY_RUNNING"));
    }

    @Test
    void requiresUuidIdempotencyHeader() throws Exception {
        mockMvc.perform(post("/api/admin/content-batch/run")
                        .principal(() -> "admin-login"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/content-batch/run")
                        .header("Idempotency-Key", "not-a-uuid")
                        .principal(() -> "admin-login"))
                .andExpect(status().isBadRequest());
    }

    private TaskRun run(Long adminId) {
        return TaskRun.queued(
                TaskType.CONTENT_SYNC,
                TriggerType.ADMIN_TRIGGERED,
                adminId,
                IDEMPOTENCY_KEY,
                "fingerprint",
                TaskType.CONTENT_SYNC.name(),
                Instant.parse("2026-08-23T00:00:00Z"));
    }
}
