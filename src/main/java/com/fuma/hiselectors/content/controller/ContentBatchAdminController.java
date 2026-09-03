package com.fuma.hiselectors.content.controller;

import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.admin.repository.AdminRepository;
import com.fuma.hiselectors.content.service.ContentBatchMode;
import com.fuma.hiselectors.content.task.ContentSyncTask;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.taskrun.dto.TaskRunResponse;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import com.fuma.hiselectors.taskrun.service.TaskRunExecutionService;
import com.fuma.hiselectors.taskrun.service.TaskStartCommand;
import com.fuma.hiselectors.taskrun.service.TaskStartResult;
import com.fuma.hiselectors.taskrun.service.TrackedTask;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.Collections;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/admin/content-batch")
@RequiredArgsConstructor
@Tag(name = "콘텐츠 배치", description = "콘텐츠 동기화 배치 수동 실행 (관리자 전용)")
public class ContentBatchAdminController {

    private final TaskRunExecutionService taskRunExecutionService;
    private final ContentSyncTask contentSyncTask;
    private final AdminRepository adminRepository;
    private final ObjectMapper objectMapper;

    /** 콘텐츠 배치를 수동으로 실행합니다. */
    @PostMapping("/run")
    public ResponseEntity<TaskRunResponse> run(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @RequestParam(defaultValue = "false") boolean fastMode,
            Principal principal) {
        Admin admin = adminRepository.findByLoginId(principal.getName())
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_NOT_FOUND));
        var businessPayload = objectMapper.createObjectNode();
        TrackedTask task = contentSyncTask;
        if (fastMode) {
            businessPayload.put("fastMode", true);
            task = contentSyncTask.fastModeTask();
        }
        TaskStartResult result = taskRunExecutionService.submit(
                new TaskStartCommand(
                        TaskType.CONTENT_SYNC,
                        TriggerType.ADMIN_TRIGGERED,
                        admin.getId(),
                        idempotencyKey,
                        businessPayload),
                task);
        if (result instanceof TaskStartResult.ActiveConflict) {
            throw new BusinessException(ErrorCode.TASK_ALREADY_RUNNING);
        }
        TaskRun run = result instanceof TaskStartResult.Created created
                ? created.run()
                : ((TaskStartResult.Replayed) result).run();
        return ResponseEntity.accepted()
                .body(TaskRunResponse.from(
                        run, Collections.singletonMap(admin.getId(), admin.getName())));
    }
}
