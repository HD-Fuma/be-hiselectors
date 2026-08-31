package com.fuma.hiselectors.performance.controller;

import com.fuma.hiselectors.performance.dto.SelectorBreakdownResponse;
import com.fuma.hiselectors.performance.dto.SelectorPerformanceResponse;
import com.fuma.hiselectors.performance.dto.SelectorPerformanceSummaryResponse;
import com.fuma.hiselectors.performance.dto.SelectorPerformanceTrendResponse;
import com.fuma.hiselectors.performance.service.SelectorPerformanceAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "셀렉터스 성과", description = "셀렉터스별 확정 매출과 우수 활동자 조회 (관리자 전용)")
@RestController
@RequestMapping("/api/admin/selector-performance")
@RequiredArgsConstructor
public class SelectorPerformanceAdminController {

    private final SelectorPerformanceAdminService selectorPerformanceAdminService;

    @Operation(summary = "셀렉터스 확정 매출 성과 목록 조회",
            description = "확정 시각 기준 매출을 내림차순으로 반환한다. 종료일은 해당 날짜 전체를 포함한다."
                    + " generationId를 생략하면 미삭제 셀렉터스 전체를 대상으로 한다.")
    @GetMapping
    public ResponseEntity<List<SelectorPerformanceResponse>> getSelectorPerformance(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long generationId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(selectorPerformanceAdminService
                .getSelectorPerformance(keyword, generationId, startDate, endDate));
    }

    @Operation(summary = "셀렉터스 성과 추이 조회",
            description = "기수 참여자의 확정 매출·주문을 일 또는 월 단위로 집계한다."
                    + " generationId를 생략하면 현재 ACTIVE 기수 참여자를 합친다."
                    + " 기간이 31일 이하면 일별, 그 외·기간 생략이면 월별(기본 최근 6개월)이다.")
    @GetMapping("/trend")
    public ResponseEntity<SelectorPerformanceTrendResponse> getTrend(
            @RequestParam(required = false) Long generationId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(selectorPerformanceAdminService
                .getTrend(generationId, startDate, endDate));
    }

    @Operation(summary = "셀렉터스 성과 대시보드 요약 조회",
            description = "기수 참여자 기준 KPI, 매출 분포, TOP 5, 유형별 성과, 워치리스트를 반환한다."
                    + " generationId를 생략하면 현재 ACTIVE 기수 참여자를 합친다.")
    @GetMapping("/summary")
    public ResponseEntity<SelectorPerformanceSummaryResponse> getSummary(
            @RequestParam(required = false) Long generationId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(selectorPerformanceAdminService
                .getSummary(generationId, startDate, endDate));
    }

    @Operation(summary = "셀렉터스 개인 상세 성과 조회",
            description = "특정 셀렉터스의 확정 매출을 상품별·캠페인별로 집계해 반환한다."
                    + " 상세 화면의 개인 성과 차트에 사용한다.")
    @GetMapping("/{selectorId}/breakdown")
    public ResponseEntity<SelectorBreakdownResponse> getBreakdown(
            @PathVariable Long selectorId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(selectorPerformanceAdminService
                .getBreakdown(selectorId, startDate, endDate));
    }
}
