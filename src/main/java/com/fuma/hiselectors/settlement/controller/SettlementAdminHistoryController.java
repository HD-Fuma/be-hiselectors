package com.fuma.hiselectors.settlement.controller;

import com.fuma.hiselectors.settlement.dto.SettlementEstimateResponse;
import com.fuma.hiselectors.settlement.dto.SelectorSettlementDetailResponse;
import com.fuma.hiselectors.settlement.service.SettlementAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 정산", description = "셀렉터스별 정산 이력 조회")
@RestController
@RequestMapping("/api/admin/settlements/selectors")
@RequiredArgsConstructor
public class SettlementAdminHistoryController {

    private final SettlementAdminService settlementAdminService;

    @Operation(summary = "셀렉터스 월별 정산 이력 조회")
    @GetMapping("/{selectorsId}/histories")
    public ResponseEntity<Page<SettlementEstimateResponse>> getHistories(
            @PathVariable Long selectorsId,
            @PageableDefault(size = 12, sort = "settlementMonth", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(settlementAdminService.getHistories(selectorsId, pageable));
    }

    @Operation(summary = "셀렉터스 정산 상세 조회")
    @GetMapping("/{selectorsId}/detail")
    public ResponseEntity<SelectorSettlementDetailResponse> getDetail(
            @PathVariable Long selectorsId,
            @PageableDefault(size = 12, sort = "settlementMonth", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(settlementAdminService.getDetail(selectorsId, pageable));
    }
}
