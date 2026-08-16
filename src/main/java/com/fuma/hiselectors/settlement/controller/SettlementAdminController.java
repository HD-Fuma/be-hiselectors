package com.fuma.hiselectors.settlement.controller;

import com.fuma.hiselectors.settlement.dto.SettlementEstimateResponse;
import com.fuma.hiselectors.settlement.dto.SettlementPaymentResponse;
import com.fuma.hiselectors.settlement.dto.SettlementRecalculationResponse;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.service.SettlementAdminService;
import com.fuma.hiselectors.settlement.service.SettlementRecalculationService;
import com.fuma.hiselectors.settlement.service.SettlementPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 정산", description = "월별 셀렉터스 정산 조회")
@RestController
@RequestMapping("/api/admin/settlements/estimates")
@RequiredArgsConstructor
public class SettlementAdminController {

    private final SettlementAdminService settlementAdminService;
    private final SettlementRecalculationService settlementRecalculationService;
    private final SettlementPaymentService settlementPaymentService;

    @Operation(summary = "월별 셀렉터스 정산 목록 조회")
    @GetMapping
    public ResponseEntity<Page<SettlementEstimateResponse>> search(
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) Long selectorsId,
            @RequestParam(required = false) SettlementStatus status,
            @PageableDefault(size = 20, sort = "selectorsId", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ResponseEntity.ok(
                settlementAdminService.search(month, selectorsId, status, pageable));
    }

    /**
     * 개발·테스트용 정합성 보정 API다. 운영 지급 배치를 대체하지 않으며,
     * 전체 기간·전체 셀렉터스 재계산이 필요할 때만 관리자가 사용한다.
     */
    @Operation(
            summary = "관리자 정산 이력 재계산 (테스트용)",
            description = "더미 데이터 정합성 보정용 API입니다. 운영 지급 배치를 대체하지 않으며, "
                    + "month와 selectorsId를 생략하면 전체 기간·전체 셀렉터스를 동기 재계산합니다.")
    @PostMapping("/recalculate")
    public ResponseEntity<SettlementRecalculationResponse> recalculate(
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) Long selectorsId,
            @RequestParam(defaultValue = "false") boolean force) {
        return ResponseEntity.ok(
                settlementRecalculationService.recalculate(month, selectorsId, force));
    }

    /** 지급 연동 없이 전전월 정산 이력을 지급 완료 또는 지급 보류로 상태 변경한다. */
    @Operation(
            summary = "정산 지급 상태 처리",
            description = "지급 연동 없이 전전월 활동월 정산 이력을 지급 완료 또는 지급 보류 상태로 변경합니다. "
                    + "month를 생략하면 현재 기준 전전월을 대상으로 처리합니다.")
    @PostMapping("/payments/process")
    public ResponseEntity<SettlementPaymentResponse> processPayment(
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        SettlementPaymentResponse response = month == null
                ? settlementPaymentService.processPreviousPreviousMonth()
                : settlementPaymentService.process(month);
        return ResponseEntity.ok(response);
    }
}
