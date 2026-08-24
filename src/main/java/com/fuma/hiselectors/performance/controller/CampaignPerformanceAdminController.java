package com.fuma.hiselectors.performance.controller;

import com.fuma.hiselectors.performance.dto.CampaignPerformanceResponse;
import com.fuma.hiselectors.performance.service.CampaignPerformanceAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "캠페인 성과", description = "캠페인 귀속 확정 매출 조회 (관리자 전용)")
@RestController
@RequestMapping("/api/admin/campaigns")
@RequiredArgsConstructor
public class CampaignPerformanceAdminController {

    private final CampaignPerformanceAdminService campaignPerformanceAdminService;

    @Operation(summary = "캠페인 성과 조회",
            description = "조회 기간을 캠페인 기간으로 제한하고, 현재 상품 그룹으로 귀속 가능한 확정 구매를 집계한다.")
    @GetMapping("/{campaignId}/performance")
    public ResponseEntity<CampaignPerformanceResponse> getPerformance(
            @PathVariable Long campaignId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(campaignPerformanceAdminService
                .getPerformance(campaignId, startDate, endDate));
    }
}
