package com.fuma.hiselectors.proposal.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.admin.repository.AdminRepository;
import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import com.fuma.hiselectors.proposal.dto.ProposalCreateRequest;
import com.fuma.hiselectors.proposal.task.ProposalEmailTaskFactory;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class ProposalControllerTest {

    private static final UUID KEY = UUID.fromString("f1e0d4d2-865f-4c5f-8dd5-3d810bbf0f53");
    private TaskRunExecutionService taskRunExecutionService;
    private ProposalEmailTaskFactory taskFactory;
    private AdminRepository adminRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        taskRunExecutionService = mock(TaskRunExecutionService.class);
        taskFactory = mock(ProposalEmailTaskFactory.class);
        adminRepository = mock(AdminRepository.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProposalController(
                        mock(com.fuma.hiselectors.proposal.service.ProposalService.class),
                        taskRunExecutionService, taskFactory, adminRepository, new ObjectMapper()))
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResultAdvice())
                .build();
    }

    @Test
    void startsProposalEmailAsAcceptedWithAdminPayloadAndIdempotencyKey() throws Exception {
        Admin admin = Admin.builder().loginId("admin-login").name("관리자").role("ADMIN").build();
        ReflectionTestUtils.setField(admin, "id", 3L);
        when(adminRepository.findByLoginId("admin-login")).thenReturn(Optional.of(admin));
        when(taskRunExecutionService.submit(any(), any())).thenReturn(new TaskStartResult.Created(run()));

        mockMvc.perform(post("/api/admin/proposals")
                        .principal(() -> "admin-login")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creatorId\":7,\"subject\":\"제목\",\"body\":\"본문\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.taskType").value("PROPOSAL_EMAIL_SEND"))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.startedBy.adminId").value(3));

        ArgumentCaptor<TaskStartCommand> command = ArgumentCaptor.forClass(TaskStartCommand.class);
        verify(taskRunExecutionService).submit(command.capture(), any());
        assertThat(command.getValue().idempotencyKey()).isEqualTo(KEY);
        assertThat(command.getValue().startedByAdminId()).isEqualTo(3L);
        assertThat(command.getValue().businessPayload().get("creatorId").intValue()).isEqualTo(7);
        assertThat(command.getValue().businessPayload().get("subject").stringValue())
                .isEqualTo("제목");
        verify(taskFactory).create("admin-login",
                new ProposalCreateRequest(7L, "제목", "본문"));
    }

    @Test
    void returnsAcceptedForIdempotentReplay() throws Exception {
        Admin admin = Admin.builder().loginId("admin-login").name("관리자").role("ADMIN").build();
        ReflectionTestUtils.setField(admin, "id", 3L);
        when(adminRepository.findByLoginId("admin-login")).thenReturn(Optional.of(admin));
        when(taskRunExecutionService.submit(any(), any())).thenReturn(new TaskStartResult.Replayed(run()));

        mockMvc.perform(post("/api/admin/proposals")
                        .principal(() -> "admin-login")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creatorId\":7}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void requiresUuidIdempotencyHeader() throws Exception {
        mockMvc.perform(post("/api/admin/proposals")
                        .principal(() -> "admin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creatorId\":7}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsConflictWhenProposalTaskIsAlreadyActive() throws Exception {
        Admin admin = Admin.builder().loginId("admin-login").name("관리자").role("ADMIN").build();
        ReflectionTestUtils.setField(admin, "id", 3L);
        when(adminRepository.findByLoginId("admin-login")).thenReturn(Optional.of(admin));
        when(taskRunExecutionService.submit(any(), any()))
                .thenReturn(new TaskStartResult.ActiveConflict(run()));

        mockMvc.perform(post("/api/admin/proposals")
                        .principal(() -> "admin-login")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creatorId\":7}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_ALREADY_RUNNING"));
    }

    private TaskRun run() {
        return TaskRun.queued(
                TaskType.PROPOSAL_EMAIL_SEND,
                TriggerType.ADMIN_TRIGGERED,
                3L,
                KEY,
                "fingerprint",
                null,
                Instant.parse("2026-08-24T00:00:00Z"));
    }
}
