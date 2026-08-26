package com.fuma.hiselectors.taskrun.controller;

import com.fuma.hiselectors.taskrun.dto.TaskRunPanelResponse;
import com.fuma.hiselectors.taskrun.dto.TaskRunResponse;
import com.fuma.hiselectors.taskrun.service.TaskRunProgressStream;
import com.fuma.hiselectors.taskrun.service.TaskRunQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/admin/task-runs")
@RequiredArgsConstructor
@Tag(name = "작업 실행 관리", description = "백그라운드 작업 실행 현황·이력·진행률 조회 (관리자 전용)")
public class TaskRunAdminController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final TaskRunQueryService taskRunQueryService;
    private final TaskRunProgressStream taskRunProgressStream;

    @GetMapping("/panel")
    public ResponseEntity<TaskRunPanelResponse> getPanel() {
        return ResponseEntity.ok(taskRunQueryService.getPanel());
    }

    @GetMapping("/recent")
    public ResponseEntity<Page<TaskRunResponse>> getRecent(
            @PageableDefault(size = DEFAULT_PAGE_SIZE) Pageable pageable) {
        return ResponseEntity.ok(taskRunQueryService.getRecent(limitPageSize(pageable)));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return taskRunProgressStream.subscribe();
    }

    @GetMapping("/{runId}")
    public ResponseEntity<TaskRunResponse> getDetail(@PathVariable UUID runId) {
        return ResponseEntity.ok(taskRunQueryService.getDetail(runId));
    }

    private Pageable limitPageSize(Pageable pageable) {
        if (pageable.isUnpaged()) {
            return PageRequest.of(0, DEFAULT_PAGE_SIZE);
        }
        if (pageable.getPageSize() <= MAX_PAGE_SIZE) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
    }
}
