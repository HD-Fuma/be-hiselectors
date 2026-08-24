package com.fuma.hiselectors.notification.controller;

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
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import com.fuma.hiselectors.notification.service.NotificationAdminService;
import com.fuma.hiselectors.notification.task.KakaoMessageSendTask;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import com.fuma.hiselectors.taskrun.service.TaskRunExecutionService;
import com.fuma.hiselectors.taskrun.service.TaskStartCommand;
import com.fuma.hiselectors.taskrun.service.TaskStartResult;
import com.fuma.hiselectors.taskrun.service.TrackedTask;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

class NotificationAdminControllerTaskRunTest {

    private static final UUID IDEMPOTENCY_KEY =
            UUID.fromString("0ac0855b-e14a-4242-b63a-50c5af788cc4");

    private TaskRunExecutionService taskRunExecutionService;
    private KakaoMessageSendTask taskFactory;
    private AdminRepository adminRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        taskRunExecutionService = mock(TaskRunExecutionService.class);
        taskFactory = mock(KakaoMessageSendTask.class);
        adminRepository = mock(AdminRepository.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationAdminController(
                        mock(NotificationAdminService.class), taskRunExecutionService, taskFactory,
                        adminRepository, new ObjectMapper()))
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResultAdvice())
                .build();
    }

    @Test
    void acceptsAReplaySafeKakaoResendAndTracksTheAdministrator() throws Exception {
        Admin admin = mock(Admin.class);
        TrackedTask task = mock(TrackedTask.class);
        when(admin.getId()).thenReturn(7L);
        when(admin.getName()).thenReturn("관리자");
        when(adminRepository.findByLoginId("admin-login")).thenReturn(Optional.of(admin));
        when(taskFactory.resend("admin-login", 35L)).thenReturn(task);
        when(taskRunExecutionService.submit(any(), any())).thenReturn(new TaskStartResult.Created(run()));

        mockMvc.perform(post("/api/admin/notifications/35/resend")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .principal(() -> "admin-login"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.taskType").value("KAKAO_MESSAGE_SEND"))
                .andExpect(jsonPath("$.data.triggerType").value("ADMIN_TRIGGERED"))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.startedBy.adminId").value(7));

        ArgumentCaptor<TaskStartCommand> command = ArgumentCaptor.forClass(TaskStartCommand.class);
        verify(taskRunExecutionService).submit(command.capture(), org.mockito.Mockito.same(task));
        assertThat(command.getValue().taskType()).isEqualTo(TaskType.KAKAO_MESSAGE_SEND);
        assertThat(command.getValue().triggerType()).isEqualTo(TriggerType.ADMIN_TRIGGERED);
        assertThat(command.getValue().startedByAdminId()).isEqualTo(7L);
        assertThat(command.getValue().idempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(command.getValue().businessPayload().get("notificationId").longValue())
                .isEqualTo(35L);
    }

    @Test
    void returnsAcceptedForAnIdempotentReplay() throws Exception {
        Admin admin = mock(Admin.class);
        TrackedTask task = mock(TrackedTask.class);
        when(admin.getId()).thenReturn(7L);
        when(admin.getName()).thenReturn("관리자");
        when(adminRepository.findByLoginId("admin-login")).thenReturn(Optional.of(admin));
        when(taskFactory.resend("admin-login", 35L)).thenReturn(task);
        when(taskRunExecutionService.submit(any(), any())).thenReturn(new TaskStartResult.Replayed(run()));

        mockMvc.perform(post("/api/admin/notifications/35/resend")
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .principal(() -> "admin-login"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
    }

    @Test
    void requiresAnIdempotencyHeader() throws Exception {
        mockMvc.perform(post("/api/admin/notifications/35/resend")
                        .principal(() -> "admin-login"))
                .andExpect(status().isBadRequest());
    }

    private TaskRun run() {
        return TaskRun.queued(
                TaskType.KAKAO_MESSAGE_SEND,
                TriggerType.ADMIN_TRIGGERED,
                7L,
                IDEMPOTENCY_KEY,
                "fingerprint",
                null,
                Instant.parse("2026-08-24T00:00:00Z"));
    }
}
