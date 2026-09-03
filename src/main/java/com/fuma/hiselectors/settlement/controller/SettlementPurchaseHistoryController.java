package com.fuma.hiselectors.settlement.controller;

import com.fuma.hiselectors.settlement.dto.SettlementPurchaseHistoryCursorResponse;
import com.fuma.hiselectors.settlement.dto.SettlementPurchaseHistoryResponse;
import com.fuma.hiselectors.settlement.service.SettlementPurchaseHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 정산", description = "정산 검증용 구매 이력 조회")
@RestController
@RequestMapping("/api/admin/settlements/purchase-histories")
@RequiredArgsConstructor
@Validated
public class SettlementPurchaseHistoryController {

    private final SettlementPurchaseHistoryService settlementPurchaseHistoryService;

    @Operation(summary = "정산 검증용 구매 이력 조회")
    @GetMapping
    public ResponseEntity<Page<SettlementPurchaseHistoryResponse>> search(
            @RequestParam(required = false) Long selectorsId,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(defaultValue = "false") boolean allMonths,
            @PageableDefault(size = 20, sort = {"purchasedAt", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(settlementPurchaseHistoryService.search(
                selectorsId, month, allMonths, pageable));
    }

    @Operation(summary = "정산 검증용 구매 이력 Cursor 조회")
    @GetMapping("/cursor")
    public ResponseEntity<SettlementPurchaseHistoryCursorResponse> searchCursor(
            @RequestParam(required = false) Long selectorsId,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(defaultValue = "false") boolean allMonths,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1 이상이어야 합니다.")
            @Max(value = 100, message = "size는 100 이하여야 합니다.") int size) {
        return ResponseEntity.ok(settlementPurchaseHistoryService.searchCursor(
                selectorsId, month, allMonths, cursor, size));
    }
}
