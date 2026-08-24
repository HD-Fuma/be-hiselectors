package com.fuma.hiselectors.application.controller;

import com.fuma.hiselectors.application.dto.AdminAiReportResponse;
import com.fuma.hiselectors.application.dto.AdminApplicationDetailResponse;
import com.fuma.hiselectors.application.dto.AdminApplicationSummaryResponse;
import com.fuma.hiselectors.application.dto.AdminApplicationTestCreateRequest;
import com.fuma.hiselectors.application.model.ApplicationStatus;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.service.ApplicationAdminService;
import com.fuma.hiselectors.application.service.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final ApplicationService applicationService;

    @Operation(summary = "테스트 지원자 등록")
    @PostMapping("/test")
    public ResponseEntity<Map<String, Long>> createTest(
            @Valid @RequestBody AdminApplicationTestCreateRequest request) {
        return ResponseEntity.status(201)
                .body(Map.of("id", applicationService.createTest(request.profileUrl())));
    }

    @Operation(summary = "지원자 목록 조회")
    @GetMapping
    public ResponseEntity<Page<AdminApplicationSummaryResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) SnsPlatform snsCode,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) Long generationId,
            @RequestParam(required = false) Boolean hasAiReport,
            @RequestParam(required = false) Boolean minimumCriteriaOnly,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(applicationAdminService.search(
                keyword,
                snsCode,
                status,
                generationId,
                hasAiReport,
                minimumCriteriaOnly,
                limitPageSize(pageable)));
    }

    @Operation(summary = "지원자 정량 평가 상세 조회")
    @GetMapping("/{applicationId}")
    public ResponseEntity<AdminApplicationDetailResponse> findDetail(
            @PathVariable Long applicationId) {
        return ResponseEntity.ok(applicationAdminService.findDetail(applicationId));
    }

    @Operation(summary = "지원자 AI 리포트 조회",
            description = "지원자 상세에서 콘텐츠 취합 AI 리포트(요약·카테고리·키워드·강점·주의 등)를 조회한다. "
                    + "아직 분석 전이면 404(REPORT_NOT_FOUND).")
    @GetMapping("/{applicationId}/ai-report")
    public ResponseEntity<AdminAiReportResponse> findAiReport(
            @PathVariable Long applicationId) {
        return ResponseEntity.ok(applicationAdminService.findAiReport(applicationId));
    }

    private Pageable limitPageSize(Pageable pageable) {
        if (pageable.isUnpaged() || pageable.getPageSize() <= MAX_PAGE_SIZE) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
    }
}
