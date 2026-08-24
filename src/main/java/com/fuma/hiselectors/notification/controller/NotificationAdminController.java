package com.fuma.hiselectors.notification.controller;

import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.admin.repository.AdminRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.notification.dto.NotificationHistoryResponse;
import com.fuma.hiselectors.notification.model.NotificationChannel;
import com.fuma.hiselectors.notification.model.NotificationStatus;
import com.fuma.hiselectors.notification.service.NotificationAdminService;
import com.fuma.hiselectors.notification.task.KakaoMessageSendTask;
import com.fuma.hiselectors.taskrun.dto.TaskRunResponse;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import com.fuma.hiselectors.taskrun.service.TaskRunExecutionService;
import com.fuma.hiselectors.taskrun.service.TaskStartCommand;
import com.fuma.hiselectors.taskrun.service.TaskStartResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.security.Principal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@Tag(name = "알림 및 메시지", description = "관리자 발송 이력 조회 및 실패 건 재발송")
@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
@Validated
public class NotificationAdminController {

    private final NotificationAdminService notificationAdminService;
    private final TaskRunExecutionService taskRunExecutionService;
    private final KakaoMessageSendTask taskFactory;
    private final AdminRepository adminRepository;
    private final ObjectMapper objectMapper;

    @Operation(summary = "알림 및 메시지 발송 이력 조회")
    @GetMapping
    public ResponseEntity<Page<NotificationHistoryResponse>> getHistory(
            @RequestParam(required = false) String purpose,
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,
            @RequestParam(required = false) String recipientKeyword,
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(notificationAdminService.getHistory(
                purpose, status, from, to, recipientKeyword, channel, page, size));
    }

    @Operation(summary = "실패한 발송 이력 재발송")
    @PostMapping("/{notificationId}/resend")
    public ResponseEntity<TaskRunResponse> resend(
            @PathVariable Long notificationId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            Principal principal) {
        String adminLoginId = principal.getName();
        Admin admin = adminRepository.findByLoginId(adminLoginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        TaskStartResult result = taskRunExecutionService.submit(
                new TaskStartCommand(
                        TaskType.KAKAO_MESSAGE_SEND,
                        TriggerType.ADMIN_TRIGGERED,
                        admin.getId(),
                        idempotencyKey,
                        objectMapper.createObjectNode().put("notificationId", notificationId)),
                taskFactory.resend(adminLoginId, notificationId));
        if (result instanceof TaskStartResult.ActiveConflict) {
            throw new BusinessException(ErrorCode.TASK_ALREADY_RUNNING);
        }
        TaskRun run = result instanceof TaskStartResult.Created created
                ? created.run()
                : ((TaskStartResult.Replayed) result).run();
        return ResponseEntity.accepted().body(TaskRunResponse.from(
                run, Collections.singletonMap(admin.getId(), admin.getName())));
    }
}
