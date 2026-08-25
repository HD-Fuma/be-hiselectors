package com.fuma.hiselectors.proposal.controller;

import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.admin.repository.AdminRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.proposal.dto.ProposalCreateRequest;
import com.fuma.hiselectors.proposal.dto.ProposalHistoryResponse;
import com.fuma.hiselectors.proposal.service.ProposalService;
import com.fuma.hiselectors.proposal.task.ProposalEmailTaskFactory;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.security.Principal;
import java.util.Collections;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Tag(name = "크리에이터 제안", description = "관리자용 크리에이터 제안 이력 조회·발송")
@RestController
@RequestMapping("/api/admin/proposals")
@RequiredArgsConstructor
@Validated
public class ProposalController {

    private final ProposalService proposalService;
    private final TaskRunExecutionService taskRunExecutionService;
    private final ProposalEmailTaskFactory taskFactory;
    private final AdminRepository adminRepository;
    private final ObjectMapper objectMapper;

    @Operation(summary = "제안 이력 목록 조회",
            description = "proposal_history + creator_pool + admin 을 조인해 최신순으로 반환한다.")
    @GetMapping
    public ResponseEntity<Page<ProposalHistoryResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(proposalService.list(PageRequest.of(page, size)));
    }

    @Operation(summary = "제안 메일 발송",
            description = "제안 메일 발송 작업을 접수하고 TaskRun 식별자를 반환한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "제안 메일 발송 작업 접수"),
            @ApiResponse(responseCode = "400", description = "Idempotency-Key 누락 또는 형식 오류"),
            @ApiResponse(responseCode = "409", description = "멱등 키 충돌")
    })
    @PostMapping
    public ResponseEntity<TaskRunResponse> propose(
            Principal principal,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody ProposalCreateRequest request) {
        Admin admin = adminRepository.findByLoginId(principal.getName())
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_NOT_FOUND));
        TaskStartResult result = taskRunExecutionService.submit(
                new TaskStartCommand(
                        TaskType.PROPOSAL_EMAIL_SEND,
                        TriggerType.ADMIN_TRIGGERED,
                        admin.getId(),
                        idempotencyKey,
                        payload(request)),
                taskFactory.create(principal.getName(), request));
        TaskRun run;
        if (result instanceof TaskStartResult.Created created) {
            run = created.run();
        } else if (result instanceof TaskStartResult.Replayed replayed) {
            run = replayed.run();
        } else {
            throw new BusinessException(ErrorCode.TASK_ALREADY_RUNNING);
        }
        return ResponseEntity.accepted().body(TaskRunResponse.from(
                run, Collections.singletonMap(admin.getId(), admin.getName())));
    }

    private JsonNode payload(ProposalCreateRequest request) {
        return objectMapper.createObjectNode()
                .put("creatorId", request.creatorId())
                .put("subject", request.subject())
                .put("body", request.body());
    }
}
