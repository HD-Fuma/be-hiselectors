package com.fuma.hiselectors.inspection.controller;

import com.fuma.hiselectors.inspection.dto.ViolationActionResponse;
import com.fuma.hiselectors.inspection.service.ViolationAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 콘텐츠 위반", description = "콘텐츠 위반 확정과 오탐 처리")
@RestController
@RequestMapping("/api/admin/violations")
@RequiredArgsConstructor
public class AdminViolationController {

    private final ViolationAdminService violationAdminService;

    @Operation(summary = "위반 확정 및 수정 요청")
    @PatchMapping("/{violationId}/confirm")
    public ResponseEntity<ViolationActionResponse> confirm(
            @PathVariable Long violationId, Principal principal) {
        return ResponseEntity.ok(
                violationAdminService.confirm(violationId, principal.getName()));
    }

    @Operation(summary = "위반 후보 오탐 처리")
    @PatchMapping("/{violationId}/dismiss")
    public ResponseEntity<ViolationActionResponse> dismiss(@PathVariable Long violationId) {
        return ResponseEntity.ok(violationAdminService.dismiss(violationId));
    }
}
