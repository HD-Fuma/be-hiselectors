package com.fuma.hiselectors.inspection.controller;

import com.fuma.hiselectors.inspection.service.ContentInspectionExecutionService;
import com.fuma.hiselectors.inspection.service.ContentInspectionExecutionService.InspectionResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 콘텐츠 검수", description = "콘텐츠 버전 전체 검수")
@RestController
@RequestMapping("/api/admin/content-versions")
@RequiredArgsConstructor
public class AdminContentInspectionController {

    private final ContentInspectionExecutionService inspectionService;

    @Operation(summary = "콘텐츠 버전 검수 실행")
    @PostMapping("/{contentVersionId}/inspect")
    public ResponseEntity<InspectionResult> inspect(@PathVariable Long contentVersionId) {
        return ResponseEntity.ok(inspectionService.inspect(contentVersionId));
    }
}
