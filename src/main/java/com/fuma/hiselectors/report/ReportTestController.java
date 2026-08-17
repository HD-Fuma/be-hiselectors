package com.fuma.hiselectors.report;

import com.fuma.hiselectors.report.model.ReportBase;
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

@Tag(name = "리포트 (테스트)", description = "콘텐츠 분석 → application_report/creator_report 저장")
@Profile("local")
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportTestController {

    private final ReportService reportService;

    @Operation(summary = "분석 후 리포트 저장",
            description = "youtube videoId 를 Gemini+로컬엔진으로 분석해 맥락에 맞는 report 테이블에 저장한다. "
                    + "context=APPLICATION 이면 application_report(application_id 필수), "
                    + "CREATOR 이면 creator_report 에 저장한다.")
    @PostMapping("/analyze")
    public ResponseEntity<ReportBase> analyze(
            @Parameter(description = "요청 맥락", example = "CREATOR")
            @RequestParam ReportContext context,
            @Parameter(description = "application_id 또는 creator_id", example = "1")
            @RequestParam Long targetId,
            @Parameter(description = "SNS 코드", example = "YOUTUBE")
            @RequestParam(defaultValue = "YOUTUBE") String snsCode,
            @Parameter(description = "콘텐츠 ID (유튜브 videoId)", example = "uqVIyVablhQ")
            @RequestParam String snsContentId) {
        return ResponseEntity.ok(
                reportService.analyzeAndSave(context, targetId, snsCode, snsContentId));
    }
}
