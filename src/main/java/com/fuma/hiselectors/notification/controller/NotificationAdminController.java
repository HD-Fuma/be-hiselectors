package com.fuma.hiselectors.notification.controller;

import com.fuma.hiselectors.notification.dto.NotificationHistoryResponse;
import com.fuma.hiselectors.notification.dto.NotificationSendResponse;
import com.fuma.hiselectors.notification.model.NotificationStatus;
import com.fuma.hiselectors.notification.service.NotificationAdminService;
import com.fuma.hiselectors.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.security.Principal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "알림 및 메시지", description = "관리자 발송 이력 조회 및 실패 건 재발송")
@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
@Validated
public class NotificationAdminController {

    private final NotificationAdminService notificationAdminService;
    private final NotificationService notificationService;

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
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(notificationAdminService.getHistory(
                purpose, status, from, to, recipientKeyword, page, size));
    }

    @Operation(summary = "실패한 발송 이력 재발송")
    @PostMapping("/{notificationId}/resend")
    public ResponseEntity<NotificationSendResponse> resend(
            @PathVariable Long notificationId,
            Principal principal) {
        return ResponseEntity.ok(notificationService.resendFailed(
                principal.getName(), notificationId));
    }
}
