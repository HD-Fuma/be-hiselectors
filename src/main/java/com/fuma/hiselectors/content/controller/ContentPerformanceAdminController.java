package com.fuma.hiselectors.content.controller;

import com.fuma.hiselectors.content.dto.ContentPerformanceResponse;
import com.fuma.hiselectors.content.dto.ContentPerformanceSummaryResponse;
import com.fuma.hiselectors.content.service.ContentPerformanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "콘텐츠 성과", description = "활성 기수 콘텐츠별 성과 조회 (관리자 전용)")
@RestController
@RequestMapping("/api/admin/content-performance")
@RequiredArgsConstructor
@Validated
public class ContentPerformanceAdminController {

    private final ContentPerformanceService contentPerformanceService;

    @Operation(summary = "콘텐츠 업로드·유형 요약 조회",
            description = "전체·현재·직전 기수 콘텐츠 수와 전체 콘텐츠 유형별 건수를 반환한다.")
    @GetMapping("/summary")
    public ResponseEntity<ContentPerformanceSummaryResponse> getSummary() {
        return ResponseEntity.ok(contentPerformanceService.getSummary());
    }

    @Operation(summary = "활성 기수 콘텐츠 성과 목록 조회",
            description = "콘텐츠별 최신 성과와 수집 시점별 조회·좋아요·댓글 추이를 반환한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "페이지 요청 값 오류", content = @Content),
            @ApiResponse(responseCode = "409", description = "현재 활성 기수 없음", content = @Content)
    })
    @GetMapping
    public ResponseEntity<Page<ContentPerformanceResponse>> getContentPerformance(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page는 0 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1 이상이어야 합니다.")
            @Max(value = 100, message = "size는 100 이하여야 합니다.") int size) {
        return ResponseEntity.ok(
                contentPerformanceService.getCurrentGenerationPerformance(page, size));
    }
}
