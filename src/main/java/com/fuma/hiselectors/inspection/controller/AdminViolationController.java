package com.fuma.hiselectors.inspection.controller;

import com.fuma.hiselectors.inspection.dto.ViolationActionResponse;
import com.fuma.hiselectors.inspection.dto.ViolationEvidenceHistoryResponse;
import com.fuma.hiselectors.inspection.model.ViolationEvidenceHistory;
import com.fuma.hiselectors.inspection.repository.ViolationEvidenceHistoryRepository;
import com.fuma.hiselectors.inspection.service.ViolationAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final ViolationEvidenceHistoryRepository historyRepository;

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

    @Operation(summary = "위반 근거 이력 조회",
            description = "이전 버전·엔진에서 잡힌 근거를 조회만 합니다.")
    @GetMapping("/{violationId}/evidence-history")
    public ResponseEntity<List<ViolationEvidenceHistoryResponse>> evidenceHistory(
            @PathVariable Long violationId) {
        List<ViolationEvidenceHistoryResponse> history = historyRepository
                .findAllByViolationItemIdOrderByDetectedAtAscIdAsc(violationId)
                .stream()
                .map(AdminViolationController::toResponse)
                .toList();
        return ResponseEntity.ok(history);
    }

    private static ViolationEvidenceHistoryResponse toResponse(ViolationEvidenceHistory history) {
        return new ViolationEvidenceHistoryResponse(
                history.getId(),
                history.getContentVersionId(),
                history.getInspectionPolicyId(),
                history.getEvidence(),
                history.getDetectedAt());
    }
}
