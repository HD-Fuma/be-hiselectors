package com.fuma.hiselectors.performance.controller;

import com.fuma.hiselectors.performance.dto.SelectorPerformanceResponse;
import com.fuma.hiselectors.performance.service.SelectorPerformanceAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
            description = "확정 시각 기준 매출을 내림차순으로 반환한다. 종료일은 해당 날짜 전체를 포함한다.")
    @GetMapping
    public ResponseEntity<List<SelectorPerformanceResponse>> getSelectorPerformance(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(selectorPerformanceAdminService
                .getSelectorPerformance(keyword, startDate, endDate));
    }
}
