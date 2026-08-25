package com.fuma.hiselectors.settlement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.fuma.hiselectors.settlement.dto.SettlementPaymentResponse;
import com.fuma.hiselectors.settlement.service.SettlementAdminService;
import com.fuma.hiselectors.settlement.service.SettlementPaymentService;
import com.fuma.hiselectors.settlement.task.SettlementRecalculationTask;
import com.fuma.hiselectors.settlement.task.SettlementRecalculationTaskFactory;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import com.fuma.hiselectors.taskrun.service.TaskRunExecutionService;
import com.fuma.hiselectors.taskrun.service.TaskStartCommand;
import com.fuma.hiselectors.taskrun.service.TaskStartResult;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

class SettlementAdminControllerTest {

    private static final UUID KEY =
            UUID.fromString("8ad40b7c-68ce-4aaa-a2e8-83db777fdb7e");

    private TaskRunExecutionService taskRunExecutionService;
    private SettlementRecalculationTaskFactory taskFactory;
    private AdminRepository adminRepository;
    private SettlementPaymentService settlementPaymentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        taskRunExecutionService = mock(TaskRunExecutionService.class);
        taskFactory = mock(SettlementRecalculationTaskFactory.class);
        adminRepository = mock(AdminRepository.class);
        settlementPaymentService = mock(SettlementPaymentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SettlementAdminController(
                        mock(SettlementAdminService.class), settlementPaymentService,
                        taskRunExecutionService, taskFactory, adminRepository, new ObjectMapper()))
                .setControllerAdvice(new ApiResultAdvice(), new GlobalExceptionHandler())
                .build();
    }

    @Test
    void acceptsAdminRecalculationWithStablePayloadAndIdempotency() throws Exception {
        Admin admin = mock(Admin.class);
        SettlementRecalculationTask task = mock(SettlementRecalculationTask.class);
        when(admin.getId()).thenReturn(7L);
        when(admin.getName()).thenReturn("관리자");
        when(adminRepository.findByLoginId("admin-login")).thenReturn(Optional.of(admin));
        when(taskFactory.create(YearMonth.of(2026, 7), 10L, true)).thenReturn(task);
        when(taskRunExecutionService.submit(any(), eq(task))).thenReturn(new TaskStartResult.Created(run()));

        mockMvc.perform(post("/api/admin/settlements/estimates/recalculate")
                        .principal(() -> "admin-login")
                        .header("Idempotency-Key", KEY)
                        .param("activityMonth", "2026-07")
                        .param("selectorsId", "10")
                        .param("force", "true"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.taskType").value("SETTLEMENT_CALCULATION"))
                .andExpect(jsonPath("$.data.triggerType").value("ADMIN_TRIGGERED"))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.startedBy.adminId").value(7))
                .andExpect(jsonPath("$.data.startedBy.name").value("관리자"));

        ArgumentCaptor<TaskStartCommand> command = ArgumentCaptor.forClass(TaskStartCommand.class);
        verify(taskRunExecutionService).submit(command.capture(), eq(task));
        assertThat(command.getValue().taskType()).isEqualTo(TaskType.SETTLEMENT_CALCULATION);
        assertThat(command.getValue().triggerType()).isEqualTo(TriggerType.ADMIN_TRIGGERED);
        assertThat(command.getValue().startedByAdminId()).isEqualTo(7L);
        assertThat(command.getValue().idempotencyKey()).isEqualTo(KEY);
        assertThat(command.getValue().businessPayload().get("activityMonth").stringValue())
                .isEqualTo("2026-07");
        assertThat(command.getValue().businessPayload().get("selectorsId").longValue())
                .isEqualTo(10L);
        assertThat(command.getValue().businessPayload().get("force").booleanValue()).isTrue();
    }

    @Test
    void acceptsIdempotentReplayWithNullScopeInPayload() throws Exception {
        Admin admin = mock(Admin.class);
        when(admin.getId()).thenReturn(7L);
        when(admin.getName()).thenReturn("관리자");
        when(adminRepository.findByLoginId("admin-login")).thenReturn(Optional.of(admin));
        when(taskRunExecutionService.submit(any(), any())).thenReturn(new TaskStartResult.Replayed(run()));

        mockMvc.perform(post("/api/admin/settlements/estimates/recalculate")
                        .principal(() -> "admin-login")
                        .header("Idempotency-Key", KEY))
                .andExpect(status().isAccepted());

        ArgumentCaptor<TaskStartCommand> command = ArgumentCaptor.forClass(TaskStartCommand.class);
        verify(taskRunExecutionService).submit(command.capture(), any());
        assertThat(command.getValue().businessPayload().get("activityMonth").isNull()).isTrue();
        assertThat(command.getValue().businessPayload().get("selectorsId").isNull()).isTrue();
        assertThat(command.getValue().businessPayload().get("force").booleanValue()).isFalse();
        verify(taskFactory).create(null, null, false);
    }

    @Test
    void returnsConflictWhenSettlementCalculationIsAlreadyActive() throws Exception {
        Admin admin = mock(Admin.class);
        when(admin.getId()).thenReturn(7L);
        when(adminRepository.findByLoginId("admin-login")).thenReturn(Optional.of(admin));
        when(taskRunExecutionService.submit(any(), any()))
                .thenReturn(new TaskStartResult.ActiveConflict(run()));

        mockMvc.perform(post("/api/admin/settlements/estimates/recalculate")
                        .principal(() -> "admin-login")
                        .header("Idempotency-Key", KEY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_ALREADY_RUNNING"));
    }

    @Test
    void requiresUuidIdempotencyHeader() throws Exception {
        mockMvc.perform(post("/api/admin/settlements/estimates/recalculate")
                        .principal(() -> "admin-login"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/settlements/estimates/recalculate")
                        .principal(() -> "admin-login")
                        .header("Idempotency-Key", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void processesPaymentForRequestedMonth() throws Exception {
        when(settlementPaymentService.process(YearMonth.of(2026, 6)))
                .thenReturn(new SettlementPaymentResponse(
                        YearMonth.of(2026, 6), YearMonth.of(2026, 4), 3, 2, 1, 0, 0));

        mockMvc.perform(post("/api/admin/settlements/estimates/payments/process")
                        .param("paymentMonth", "2026-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentMonth").value("2026-06"))
                .andExpect(jsonPath("$.data.settledCount").value(2))
                .andExpect(jsonPath("$.data.heldCount").value(1));

        verify(settlementPaymentService).process(YearMonth.of(2026, 6));
    }

    private TaskRun run() {
        return TaskRun.queued(
                TaskType.SETTLEMENT_CALCULATION,
                TriggerType.ADMIN_TRIGGERED,
                7L,
                KEY,
                "fingerprint",
                TaskType.SETTLEMENT_CALCULATION.name(),
                Instant.parse("2026-08-24T00:00:00Z"));
    }
}
