package com.fuma.hiselectors.application.controller;

import com.fuma.hiselectors.application.dto.ApplicationResponse;
import com.fuma.hiselectors.application.dto.ApplicationStatusUpdateRequest;
import com.fuma.hiselectors.application.service.ApplicationApprovalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/applications")
public class ApplicationStatusAdminController {

    private final ApplicationApprovalService applicationApprovalService;

    @PatchMapping("/{applicationId}/status")
    public ResponseEntity<ApplicationResponse> updateStatus(
            @PathVariable Long applicationId,
            @Valid @RequestBody ApplicationStatusUpdateRequest request) {
        return ResponseEntity.ok(applicationApprovalService.updateStatus(applicationId, request));
    }
}
