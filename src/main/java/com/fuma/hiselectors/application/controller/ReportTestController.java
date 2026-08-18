package com.fuma.hiselectors.application.controller;

import com.fuma.hiselectors.application.model.ApplicationReport;
import com.fuma.hiselectors.application.service.ApplicationReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "리포트 (테스트)", description = "콘텐츠 분석 → 캐시 누적 (지원자 단위 취합은 별도)")
@Profile("local")
@RestController
@RequestMapping("/api/application")
@RequiredArgsConstructor
public class ReportTestController {

    private final ApplicationReportService applicationReportService;

    @Operation(summary = "콘텐츠 분석 후 캐시 누적",
            description = "youtube videoId 를 Gemini+로컬엔진으로 분석해 application 단위 캐시에 쌓는다. "
                    + "여러 콘텐츠 + 사람 평가를 취합해 지원자 리포트를 만드는 단계에서 application_report 에 저장한다. "
                    + "카테고리가 더현대 공식 코드로 안 잡히면 422(REPORT_CATEGORY_NOT_SUPPORTED)로 응답한다.")
    @PostMapping("/{applicationId}/content-report")
    public ResponseEntity<ApplicationReport> analyzeContent(
            @Parameter(description = "application_id", example = "1")
            @PathVariable Long applicationId,
            @Parameter(description = "SNS 코드", example = "YOUTUBE")
            @RequestParam(defaultValue = "YOUTUBE") String snsCode,
            @Parameter(description = "콘텐츠 ID (유튜브 videoId)", example = "uqVIyVablhQ")
            @RequestParam String snsContentId) {
        return ResponseEntity.ok(
                applicationReportService.analyzeContent(applicationId, snsCode, snsContentId));
    }

    @Operation(summary = "캐시 누적 확인 (임시)",
            description = "applicationId 키에 쌓인 콘텐츠 분석 결과 목록을 반환한다. 취합 로직 붙이기 전 검증용.")
    @GetMapping("/{applicationId}/content-report")
    public ResponseEntity<List<ApplicationReport>> cachedReports(
            @Parameter(description = "application_id", example = "1")
            @PathVariable Long applicationId) {
        return ResponseEntity.ok(applicationReportService.getCachedReports(applicationId));
    }
}
