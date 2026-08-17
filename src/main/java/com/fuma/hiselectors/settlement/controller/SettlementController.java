package com.fuma.hiselectors.settlement.controller;

import com.fuma.hiselectors.settlement.dto.SettlementEstimateResponse;
import com.fuma.hiselectors.settlement.dto.SettlementHistoryListResponse;
import com.fuma.hiselectors.settlement.service.SettlementEstimateService;
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

@Tag(name = "정산", description = "셀렉터스 예상 수수료 조회")
@RestController
@RequestMapping("/api/settlements/estimates")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementEstimateService settlementEstimateService;

    @Operation(summary = "월별 예상 수수료 조회")
    @GetMapping
    public ResponseEntity<SettlementEstimateResponse> getEstimate(
            Principal principal,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth activityMonth) {
        return ResponseEntity.ok(
                settlementEstimateService.getEstimate(principal.getName(), activityMonth));
    }

    @Operation(summary = "연도별 정산 이력 조회")
    @GetMapping("/histories")
    public ResponseEntity<SettlementHistoryListResponse> getHistories(
            Principal principal,
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(
                settlementEstimateService.getHistories(principal.getName(), year));
    }
}
