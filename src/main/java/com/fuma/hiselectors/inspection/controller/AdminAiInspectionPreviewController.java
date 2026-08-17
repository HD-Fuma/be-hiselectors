package com.fuma.hiselectors.inspection.controller;

import com.fuma.hiselectors.inspection.detector.AiViolationDetector;
import com.fuma.hiselectors.inspection.model.AiInspectionResult;
import com.fuma.hiselectors.inspection.model.ContentReportData;
import com.fuma.hiselectors.inspection.model.EvidenceLocation;
import com.fuma.hiselectors.inspection.model.ViolationTypeCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 콘텐츠 검수", description = "콘텐츠 검수 및 AI 미리보기")
@RestController
@RequestMapping("/api/admin/inspections")
@RequiredArgsConstructor
public class AdminAiInspectionPreviewController {

    private final AiViolationDetector aiViolationDetector;

    @Operation(summary = "텍스트 AI 검수 미리보기",
            description = "DB에 저장하지 않고 텍스트를 Gemini로 검수합니다.")
    @PostMapping("/preview")
    public ResponseEntity<PreviewResponse> preview(
            @Valid @RequestBody PreviewRequest request) {
        return ResponseEntity.ok(PreviewResponse.from(
                aiViolationDetector.inspectText(request.text())));
    }

    public record PreviewRequest(
            @NotBlank(message = "검수할 내용을 입력해주세요.")
            String text
    ) { }

    public record PreviewResponse(
            ContentReportData report,
            List<PreviewViolation> violations
    ) {
        static PreviewResponse from(AiInspectionResult result) {
            return new PreviewResponse(
                    result.report(),
                    result.violations().stream()
                            .map(violation -> new PreviewViolation(
                                    violation.type(),
                                    violation.evidence().reason(),
                                    violation.evidence().confidence(),
                                    violation.evidence().locations()))
                            .toList());
        }
    }

    public record PreviewViolation(
            ViolationTypeCode violationType,
            String reason,
            Double confidence,
            List<EvidenceLocation> locations
    ) { }
}
