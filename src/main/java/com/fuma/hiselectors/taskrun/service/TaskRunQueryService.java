package com.fuma.hiselectors.taskrun.service;

import com.fuma.hiselectors.admin.repository.AdminRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.taskrun.dto.TaskRunPanelResponse;
import com.fuma.hiselectors.taskrun.dto.TaskRunResponse;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskRunStatus;
import com.fuma.hiselectors.taskrun.repository.TaskRunRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskRunQueryService {

    private static final List<TaskRunStatus> ACTIVE_STATUSES = List.of(
            TaskRunStatus.QUEUED, TaskRunStatus.RUNNING);
    private static final List<TaskRunStatus> TERMINAL_STATUSES = List.of(
            TaskRunStatus.SUCCEEDED,
            TaskRunStatus.PARTIAL_FAILED,
            TaskRunStatus.FAILED,
            TaskRunStatus.STALE);
    private static final Duration PANEL_HISTORY_WINDOW = Duration.ofMinutes(10);
    private static final int PANEL_HISTORY_LIMIT = 20;

    private final TaskRunRepository taskRunRepository;
    private final AdminRepository adminRepository;
    private final Clock clock;

    public TaskRunPanelResponse getPanel() {
        Instant now = clock.instant();
        List<TaskRun> runs = new java.util.ArrayList<>(
                taskRunRepository.findActiveRuns(ACTIVE_STATUSES));
        runs.addAll(taskRunRepository.findRecentTerminalRuns(
                TERMINAL_STATUSES,
                now.minus(PANEL_HISTORY_WINDOW),
                PageRequest.of(0, PANEL_HISTORY_LIMIT)));
        Map<Long, String> adminNames = findAdminNames(runs);
        return new TaskRunPanelResponse(
                runs.stream().map(run -> TaskRunResponse.from(run, adminNames)).toList(),
                now);
    }

    public Page<TaskRunResponse> getRecent(Pageable pageable) {
        Page<TaskRun> runs = taskRunRepository
                .findTerminalRuns(TERMINAL_STATUSES, pageable);
        Map<Long, String> adminNames = findAdminNames(runs.getContent());
        return runs.map(run -> TaskRunResponse.from(run, adminNames));
    }

    public TaskRunResponse getDetail(java.util.UUID runId) {
        TaskRun run = taskRunRepository.findByRunId(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_RUN_NOT_FOUND));
        return TaskRunResponse.from(run, findAdminNames(List.of(run)));
    }

    private Map<Long, String> findAdminNames(List<TaskRun> runs) {
        List<Long> adminIds = runs.stream()
                .map(TaskRun::getStartedByAdminId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (adminIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new LinkedHashMap<>();
        adminRepository.findNamesByIdIn(adminIds)
                .forEach(admin -> names.put(admin.getId(), admin.getName()));
        return names;
    }
}
