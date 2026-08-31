package com.fuma.hiselectors.taskrun.queue;

import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.admin.repository.AdminRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.taskrun.dto.TaskRunResponse;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskRunStatus;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import com.fuma.hiselectors.taskrun.service.TaskRunExecutionService;
import com.fuma.hiselectors.taskrun.service.TaskRunTaskResolver;
import com.fuma.hiselectors.taskrun.service.TaskStartCommand;
import com.fuma.hiselectors.taskrun.service.TaskStartResult;
import com.fuma.hiselectors.taskrun.service.TrackedTask;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.Collections;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@RestController
@RequestMapping("/api/admin/task-runs")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "task-queue.enabled", havingValue = "true")
@Tag(name = "작업 실행 관리")
public class TaskQueueRetryController {

    private final TaskQueueState state;
    private final TaskRunTaskResolver resolver;
    private final TaskRunExecutionService execution;
    private final AdminRepository adminRepository;
    private final ObjectMapper mapper;

    /**
     * 안전한 종료 작업의 원래 범위 전체를 새 실행으로 제출한다. 실패 ID만 재실행하는 API가 아니다.
     * 원본 상태와 DLQ 메시지를 변경하지 않으며, 기존 실행과 다른 멱등 키가 필요하다.
     */
    @Operation(summary = "안전한 실패 작업의 원래 범위 전체 재실행",
            description = "실패 ID만 재실행하지 않습니다. 큐 기반 FAILED/PARTIAL_FAILED/STALE 중 안전한 "
                    + "크리에이터 수집·콘텐츠 동기화 작업만 새 실행으로 제출합니다. 원본과 다른 Idempotency-Key가 "
                    + "필요하며 같은 새 키의 재요청은 기존 결과를 반환합니다. 콘텐츠 리포트는 중단된 "
                    + "INSPECTING 항목 확인과 도메인 복구가 먼저 필요합니다. 정산·메일·카카오 발송도 "
                    + "각 도메인의 복구 절차를 사용해야 합니다. 원본 실행과 DLQ는 변경하지 않습니다.")
    @PostMapping("/{runId}/retry")
    public ResponseEntity<TaskRunResponse> retry(
            @PathVariable UUID runId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            Principal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Admin admin = adminRepository.findByLoginId(principal.getName())
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_NOT_FOUND));
        TaskRun source = state.get(runId);
        TaskRunStatus status = source.getStatus();
        if (!source.isQueueManaged()
                || (status != TaskRunStatus.FAILED && status != TaskRunStatus.PARTIAL_FAILED
                    && status != TaskRunStatus.STALE)
                || !resolver.automaticRetrySafe(source.getTaskType())
                || idempotencyKey.equals(source.getIdempotencyKey())) {
            throw new BusinessException(ErrorCode.INVALID_TASK_RUN_TRANSITION);
        }

        JsonNode payload;
        TrackedTask task;
        try {
            task = resolver.resolve(source);
            payload = mapper.readTree(source.getBusinessPayload());
        } catch (JacksonException | IllegalArgumentException exception) {
            // Persisted commands may contain personal data: do not echo parsing details or raw JSON.
            throw new BusinessException(ErrorCode.INVALID_TASK_RUN_TRANSITION);
        }
        TaskStartResult result = execution.submit(new TaskStartCommand(
                source.getTaskType(), TriggerType.ADMIN_TRIGGERED, admin.getId(), idempotencyKey, payload), task);
        if (result instanceof TaskStartResult.ActiveConflict) {
            throw new BusinessException(ErrorCode.TASK_ALREADY_RUNNING);
        }
        TaskRun run = result instanceof TaskStartResult.Created created
                ? created.run() : ((TaskStartResult.Replayed) result).run();
        log.info("Task queue retry sourceRunId={} newRunId={}", source.getRunId(), run.getRunId());
        return ResponseEntity.accepted().body(TaskRunResponse.from(
                run, Collections.singletonMap(admin.getId(), admin.getName())));
    }
}
