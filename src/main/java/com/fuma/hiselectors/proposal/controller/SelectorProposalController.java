package com.fuma.hiselectors.proposal.controller;

import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.admin.repository.AdminRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.proposal.dto.SelectorProposalRequest;
import com.fuma.hiselectors.proposal.task.SelectorProposalEmailTaskFactory;
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
import java.security.Principal;
import java.util.Collections;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Tag(name = "셀렉터스 제안", description = "관리자용 셀렉터스 다건 제안 메일 발송")
@RestController
@RequestMapping("/api/admin/selector-proposals")
@RequiredArgsConstructor
@Validated
public class SelectorProposalController {

    private final TaskRunExecutionService taskRunExecutionService;
    private final SelectorProposalEmailTaskFactory taskFactory;
    private final AdminRepository adminRepository;
    private final ObjectMapper objectMapper;

    @Operation(summary = "셀렉터스 제안 메일 발송",
            description = "선택한 셀렉터스들에게 제안 메일 발송 작업을 접수하고 TaskRun 식별자를 반환한다."
                    + " 제목·본문을 생략하면 셀렉터스용 기본 템플릿을 사용한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "제안 메일 발송 작업 접수"),
            @ApiResponse(responseCode = "400", description = "Idempotency-Key 누락 또는 형식 오류"),
            @ApiResponse(responseCode = "409", description = "멱등 키 충돌")
    })
    @PostMapping
    public ResponseEntity<TaskRunResponse> propose(
            Principal principal,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody SelectorProposalRequest request) {
        Admin admin = adminRepository.findByLoginId(principal.getName())
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_NOT_FOUND));
        TaskStartResult result = taskRunExecutionService.submit(
                new TaskStartCommand(
                        TaskType.SELECTOR_PROPOSAL_EMAIL_SEND,
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

    private JsonNode payload(SelectorProposalRequest request) {
        ObjectNode node = objectMapper.createObjectNode();
        ArrayNode ids = node.putArray("selectorIds");
        request.selectorIds().forEach(id -> ids.add(id.longValue()));
        if (request.subject() != null) {
            node.put("subject", request.subject());
            node.put("body", request.body());
        }
        return node;
    }
}
