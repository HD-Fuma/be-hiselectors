package com.fuma.hiselectors.performance.controller;

import com.fuma.hiselectors.performance.dto.PerformanceSummaryResponse;
import com.fuma.hiselectors.performance.dto.ProductPerformanceListResponse;
import com.fuma.hiselectors.performance.service.PerformanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "셀렉터스 성과", description = "로그인 셀렉터스의 월별 성과 조회")
@RestController
@RequestMapping("/api/performance")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceService performanceService;

    @Operation(summary = "월별 성과 요약 조회")
    @GetMapping("/summary")
    public ResponseEntity<PerformanceSummaryResponse> getSummary(
            Principal principal,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth activityMonth) {
        return ResponseEntity.ok(
                performanceService.getSummary(principal.getName(), activityMonth));
    }

    @Operation(summary = "월별 상품 성과 조회")
    @GetMapping("/products")
    public ResponseEntity<ProductPerformanceListResponse> getProducts(
            Principal principal,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth activityMonth) {
        return ResponseEntity.ok(
                performanceService.getProducts(principal.getName(), activityMonth));
    }
}
