package com.fuma.hiselectors.performance.controller;

import com.fuma.hiselectors.performance.dto.DashboardSalesSettlementTrendResponse;
import com.fuma.hiselectors.performance.service.DashboardSalesSettlementTrendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 대시보드", description = "관리자 메인 대시보드 전용 조회")
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardPerformanceAdminController {

    private final DashboardSalesSettlementTrendService trendService;

    @Operation(
            summary = "최근 매출·예상 정산액 추이 조회",
            description = "현재 활성 기수 참여자의 확정 매출과 예상 정산액을 일별로 반환한다."
                    + " 조회 기간은 1일 이상 7일 이하다.")
    @GetMapping("/sales-settlement-trend")
    public ResponseEntity<DashboardSalesSettlementTrendResponse> getSalesSettlementTrend(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(trendService.getTrend(startDate, endDate));
    }
}
