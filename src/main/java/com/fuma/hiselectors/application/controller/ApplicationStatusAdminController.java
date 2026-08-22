package com.fuma.hiselectors.application.controller;

import com.fuma.hiselectors.application.dto.ApplicationResponse;
import com.fuma.hiselectors.application.dto.ApplicationStatusUpdateRequest;
import com.fuma.hiselectors.application.service.ApplicationApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "지원자 승인/반려", description = "관리자용 지원자 수동 승인·반려 처리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/applications")
public class ApplicationStatusAdminController {

    private final ApplicationApprovalService applicationApprovalService;

    @Operation(summary = "지원자 승인/반려",
            description = "status 를 APPROVED(승인) 또는 REJECTED(반려) 로 변경한다. "
                    + "승인 시 셀렉터스로 활성화되고 SNS 계정·기수 매핑이 동기화된다. "
                    + "PENDING 으로의 되돌림이나 이미 확정된 상태의 재변경은 허용하지 않는다.")
    @PatchMapping("/{applicationId}/status")
    public ResponseEntity<ApplicationResponse> updateStatus(
            @PathVariable Long applicationId,
            @Valid @RequestBody ApplicationStatusUpdateRequest request,
            Principal principal) {
        return ResponseEntity.ok(applicationApprovalService.updateStatus(
                applicationId, request, principal.getName()));
    }
}
