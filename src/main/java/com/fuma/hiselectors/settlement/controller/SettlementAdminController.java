package com.fuma.hiselectors.settlement.controller;

import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.admin.repository.AdminRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.settlement.dto.SettlementAdminSummaryResponse;
import com.fuma.hiselectors.settlement.dto.SettlementEstimateResponse;
import com.fuma.hiselectors.settlement.dto.SettlementPaymentResponse;
import com.fuma.hiselectors.settlement.model.SettlementStatus;
import com.fuma.hiselectors.settlement.service.SettlementAdminService;
import com.fuma.hiselectors.settlement.service.SettlementPaymentService;
import com.fuma.hiselectors.settlement.task.SettlementRecalculationTaskFactory;
import com.fuma.hiselectors.taskrun.dto.TaskRunResponse;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import com.fuma.hiselectors.taskrun.service.TaskRunExecutionService;
import com.fuma.hiselectors.taskrun.service.TaskStartCommand;
import com.fuma.hiselectors.taskrun.service.TaskStartResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.time.YearMonth;
import java.util.Collections;
import java.util.UUID;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Tag(name = "관리자 정산", description = "월별 셀렉터스 정산 조회")
@RestController
@RequestMapping("/api/admin/settlements/estimates")
@RequiredArgsConstructor
public class SettlementAdminController {

    private final SettlementAdminService settlementAdminService;
    private final SettlementPaymentService settlementPaymentService;
    private final TaskRunExecutionService taskRunExecutionService;
    private final SettlementRecalculationTaskFactory recalculationTaskFactory;
    private final AdminRepository adminRepository;
    private final ObjectMapper objectMapper;

    @Operation(summary = "월별 셀렉터스 정산 목록 조회")
    @GetMapping
    public ResponseEntity<Page<SettlementEstimateResponse>> search(
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth activityMonth,
            @RequestParam(required = false) Long selectorsId,
            @RequestParam(required = false) SettlementStatus status,
            @RequestParam(defaultValue = "false") boolean nonZeroSettlementAmount,
            @PageableDefault(size = 20, sort = "selectorsId", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ResponseEntity.ok(
                settlementAdminService.search(
                        activityMonth, selectorsId, status, nonZeroSettlementAmount, pageable));
    }

    @Operation(summary = "월별 셀렉터스 정산 요약 조회")
    @GetMapping("/summary")
    public ResponseEntity<SettlementAdminSummaryResponse> summarize(
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth activityMonth,
            @RequestParam(required = false) Long selectorsId,
            @RequestParam(required = false) SettlementStatus status) {
        return ResponseEntity.ok(
                settlementAdminService.summarize(activityMonth, selectorsId, status));
    }

    /**
     * 개발·테스트용 정합성 보정 API다. 운영 지급 배치를 대체하지 않으며,
     * 전체 기간·전체 셀렉터스 재계산이 필요할 때만 관리자가 사용한다.
     */
    @Operation(
            summary = "관리자 정산 이력 재계산 (테스트용)",
            description = "더미 데이터 정합성 보정용 API입니다. 금액·확정 상태를 다시 계산한 뒤, "
                    + "지급일이 지난 지급 대기 건은 현재 지급월 기준으로 캐치업합니다. "
                    + "activityMonth와 selectorsId를 생략하면 전체 기간·전체 셀렉터스 재계산 작업을 접수합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "정산 재계산 작업 접수"),
            @ApiResponse(responseCode = "400", description = "Idempotency-Key 누락 또는 형식 오류"),
            @ApiResponse(responseCode = "409", description = "같은 정산 계산 작업 실행 중 또는 멱등 키 충돌")
    })
    @PostMapping("/recalculate")
    public ResponseEntity<TaskRunResponse> recalculate(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            Principal principal,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth activityMonth,
            @RequestParam(required = false) Long selectorsId,
            @RequestParam(defaultValue = "false") boolean force) {
        Admin admin = adminRepository.findByLoginId(principal.getName())
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_NOT_FOUND));
        TaskStartResult result = taskRunExecutionService.submit(
                new TaskStartCommand(
                        TaskType.SETTLEMENT_CALCULATION,
                        TriggerType.ADMIN_TRIGGERED,
                        admin.getId(),
                        idempotencyKey,
                        recalculationPayload(activityMonth, selectorsId, force)),
                recalculationTaskFactory.create(activityMonth, selectorsId, force));
        if (result instanceof TaskStartResult.ActiveConflict) {
            throw new BusinessException(ErrorCode.TASK_ALREADY_RUNNING);
        }
        TaskRun run = result instanceof TaskStartResult.Created created
                ? created.run()
                : ((TaskStartResult.Replayed) result).run();
        return ResponseEntity.accepted().body(TaskRunResponse.from(
                run, Collections.singletonMap(admin.getId(), admin.getName())));
    }

    private JsonNode recalculationPayload(
            YearMonth activityMonth, Long selectorsId, boolean force) {
        ObjectNode payload = objectMapper.createObjectNode();
        if (activityMonth == null) {
            payload.putNull("activityMonth");
        } else {
            payload.put("activityMonth", activityMonth.toString());
        }
        if (selectorsId == null) {
            payload.putNull("selectorsId");
        } else {
            payload.put("selectorsId", selectorsId);
        }
        return payload.put("force", force);
    }

    /** 지급 연동 없이 전전월 정산 이력을 지급 완료 또는 지급 보류로 상태 변경한다. */
    @Operation(
            summary = "정산 지급 상태 처리",
            description = "지급 연동 없이 전전월 활동월 정산 이력을 지급 완료 또는 지급 보류 상태로 변경합니다. "
                    + "paymentMonth를 생략하면 현재 지급월을 대상으로 처리합니다.")
    @PostMapping("/payments/process")
    public ResponseEntity<SettlementPaymentResponse> processPayment(
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth paymentMonth) {
        SettlementPaymentResponse response = paymentMonth == null
                ? settlementPaymentService.processCurrentPaymentMonth()
                : settlementPaymentService.process(paymentMonth);
        return ResponseEntity.ok(response);
    }
}
