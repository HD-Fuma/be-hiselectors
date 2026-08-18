package com.fuma.hiselectors.application.controller;

import com.fuma.hiselectors.application.model.ApplicationReport;
import com.fuma.hiselectors.application.service.ApplicationReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "리포트 (테스트)", description = "콘텐츠 분석 → application_report 저장")
@Profile("local")
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportTestController {

    private final ApplicationReportService applicationReportService;

    @Operation(summary = "분석 후 리포트 저장",
            description = "youtube videoId 를 Gemini+로컬엔진으로 분석해 application_report 에 저장한다. "
                    + "카테고리가 더현대 공식 코드로 안 잡히면 422(REPORT_CATEGORY_NOT_SUPPORTED)로 응답한다.")
    @PostMapping("/analyze")
    public ResponseEntity<ApplicationReport> analyze(
            @Parameter(description = "application_id", example = "1")
            @RequestParam Long applicationId,
            @Parameter(description = "SNS 코드", example = "YOUTUBE")
            @RequestParam(defaultValue = "YOUTUBE") String snsCode,
            @Parameter(description = "콘텐츠 ID (유튜브 videoId)", example = "uqVIyVablhQ")
            @RequestParam String snsContentId) {
        return ResponseEntity.ok(
                applicationReportService.analyzeAndSave(applicationId, snsCode, snsContentId));
    }
}
