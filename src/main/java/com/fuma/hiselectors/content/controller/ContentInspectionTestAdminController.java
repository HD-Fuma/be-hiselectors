package com.fuma.hiselectors.content.controller;

import com.fuma.hiselectors.content.dto.ContentInspectionResetResponse;
import com.fuma.hiselectors.content.service.ContentInspectionResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("local")
@RestController
@RequestMapping("/api/admin/contents")
@RequiredArgsConstructor
public class ContentInspectionTestAdminController {

    private final ContentInspectionResetService resetService;

    @Operation(summary = "현재 활동 기수 콘텐츠 검수 상태 초기화",
            description = "로컬 테스트 전용입니다. 최종 결정과 위반 판정을 초기화하며 "
                    + "패널티, 블랙리스트, 감사 이력은 유지합니다.")
    @DeleteMapping("/inspection-decisions")
    public ResponseEntity<ContentInspectionResetResponse> resetInspectionDecisions(
            @Parameter(description = "오조작 방지 확인 문구",
                    example = ContentInspectionResetService.CONFIRMATION)
            @RequestParam String confirmation) {
        return ResponseEntity.ok(resetService.resetCurrentGeneration(confirmation));
    }
}
