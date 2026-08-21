package com.fuma.hiselectors.application.controller;

import com.fuma.hiselectors.application.dto.AdminApplicationDetailResponse;
import com.fuma.hiselectors.application.dto.AdminApplicationSummaryResponse;
import com.fuma.hiselectors.application.model.ApplicationStatus;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.service.ApplicationAdminService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/applications")
public class ApplicationAdminController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ApplicationAdminService applicationAdminService;

    @Operation(summary = "지원자 목록 조회")
    @GetMapping
    public ResponseEntity<Page<AdminApplicationSummaryResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) SnsPlatform snsCode,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) Long generationId,
            @RequestParam(required = false) Boolean minimumCriteriaOnly,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(applicationAdminService.search(
                keyword,
                snsCode,
                status,
                generationId,
                minimumCriteriaOnly,
                limitPageSize(pageable)));
    }

    @Operation(summary = "지원자 정량 평가 상세 조회")
    @GetMapping("/{applicationId}")
    public ResponseEntity<AdminApplicationDetailResponse> findDetail(
            @PathVariable Long applicationId) {
        return ResponseEntity.ok(applicationAdminService.findDetail(applicationId));
    }

    private Pageable limitPageSize(Pageable pageable) {
        if (pageable.isUnpaged() || pageable.getPageSize() <= MAX_PAGE_SIZE) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
    }
}
