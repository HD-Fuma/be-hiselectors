package com.fuma.hiselectors.taskrun.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.admin.repository.AdminRepository;
import com.fuma.hiselectors.admin.repository.AdminRepository.AdminNameProjection;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.taskrun.dto.TaskRunPanelResponse;
import com.fuma.hiselectors.taskrun.dto.TaskRunResponse;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskRunStatus;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import com.fuma.hiselectors.taskrun.repository.TaskRunRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class TaskRunQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-23T03:00:00Z");
    private static final List<TaskRunStatus> ACTIVE = List.of(
            TaskRunStatus.QUEUED, TaskRunStatus.RUNNING);
    private static final List<TaskRunStatus> TERMINAL = List.of(
            TaskRunStatus.SUCCEEDED,
            TaskRunStatus.PARTIAL_FAILED,
            TaskRunStatus.FAILED,
            TaskRunStatus.STALE);

    private TaskRunRepository taskRunRepository;
    private AdminRepository adminRepository;
    private TaskRunQueryService service;

    @BeforeEach
    void setUp() {
        taskRunRepository = mock(TaskRunRepository.class);
        adminRepository = mock(AdminRepository.class);
        service = new TaskRunQueryService(
                taskRunRepository,
                adminRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void panelKeepsEveryActiveRunThenAddsAtMostTwentyRecentlyFinishedRuns() {
        List<TaskRun> activeRuns = new ArrayList<>();
        for (int index = 0; index < 21; index++) {
            activeRuns.add(queued(null, NOW.minusSeconds(100 - index)));
        }
        TaskRun terminal = running(7L, NOW.minusSeconds(30));
        terminal.setTotal(3, NOW.minusSeconds(29));
        terminal.addCounts(2, 0, 0, NOW.minusSeconds(28));
        terminal.complete(NOW.minusSeconds(20));
        when(taskRunRepository.findActiveRuns(ACTIVE))
                .thenReturn(activeRuns);
        when(taskRunRepository.findRecentTerminalRuns(
                TERMINAL,
                NOW.minus(Duration.ofMinutes(10)),
                PageRequest.of(0, 20)))
                .thenReturn(List.of(terminal));
        AdminNameProjection admin = admin(7L, "관리자");
        when(adminRepository.findNamesByIdIn(List.of(7L))).thenReturn(List.of(admin));

        TaskRunPanelResponse response = service.getPanel();

        assertThat(response.serverTime()).isEqualTo(NOW);
        assertThat(response.items()).hasSize(22);
        assertThat(response.items().subList(0, 21))
                .extracting(TaskRunResponse::status)
                .containsOnly(TaskRunStatus.QUEUED);
        assertThat(response.items().get(21)).satisfies(item -> {
            assertThat(item.status()).isEqualTo(TaskRunStatus.SUCCEEDED);
            assertThat(item.progressPercent()).isEqualTo(66);
            assertThat(item.startedBy()).isEqualTo(
                    new TaskRunResponse.StartedBy(7L, "관리자"));
        });
        verify(taskRunRepository).findActiveRuns(ACTIVE);
        verify(taskRunRepository).findRecentTerminalRuns(
                TERMINAL,
                NOW.minus(Duration.ofMinutes(10)),
                PageRequest.of(0, 20));
        verify(adminRepository).findNamesByIdIn(List.of(7L));
    }

    @Test
    void recentReturnsPagedTerminalHistoryAndResolvesAdminNamesInOneQuery() {
        PageRequest pageable = PageRequest.of(2, 5);
        TaskRun first = running(3L, NOW.minusSeconds(50));
        first.complete(NOW.minusSeconds(40));
        TaskRun second = running(4L, NOW.minusSeconds(30));
        second.fail("TEST", "failed", NOW.minusSeconds(20));
        when(taskRunRepository.findTerminalRuns(TERMINAL, pageable))
                .thenReturn(new PageImpl<>(List.of(first, second), pageable, 12));
        AdminNameProjection firstAdmin = admin(3L, "첫 관리자");
        AdminNameProjection secondAdmin = admin(4L, "둘 관리자");
        when(adminRepository.findNamesByIdIn(List.of(3L, 4L)))
                .thenReturn(List.of(firstAdmin, secondAdmin));

        Page<TaskRunResponse> response = service.getRecent(pageable);

        assertThat(response.getNumber()).isEqualTo(2);
        assertThat(response.getTotalElements()).isEqualTo(12);
        assertThat(response.getContent())
                .extracting(item -> item.startedBy().name())
                .containsExactly("첫 관리자", "둘 관리자");
        verify(adminRepository).findNamesByIdIn(List.of(3L, 4L));
    }

    @Test
    void detailPreservesAdminIdWithNullNameWhenAdminNoLongerExists() {
        TaskRun run = running(9L, NOW.minusSeconds(5));
        when(taskRunRepository.findByRunId(run.getRunId())).thenReturn(Optional.of(run));
        when(adminRepository.findNamesByIdIn(List.of(9L))).thenReturn(List.of());

        TaskRunResponse response = service.getDetail(run.getRunId());

        assertThat(response.startedBy()).isEqualTo(new TaskRunResponse.StartedBy(9L, null));
        assertThat(response.progressPercent()).isNull();
    }

    @Test
    void detailThrowsTaskRunNotFound() {
        UUID runId = UUID.randomUUID();
        when(taskRunRepository.findByRunId(runId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(runId))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.TASK_RUN_NOT_FOUND));
    }

    private TaskRun queued(Long adminId, Instant createdAt) {
        return TaskRun.queued(
                TaskType.CONTENT_SYNC,
                adminId == null ? TriggerType.SCHEDULED : TriggerType.ADMIN_TRIGGERED,
                adminId,
                UUID.randomUUID(),
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                null,
                createdAt);
    }

    private TaskRun running(Long adminId, Instant startedAt) {
        TaskRun run = queued(adminId, startedAt.minusSeconds(1));
        run.markRunning(UUID.randomUUID(), startedAt);
        return run;
    }

    private AdminNameProjection admin(Long id, String name) {
        AdminNameProjection projection = mock(AdminNameProjection.class);
        when(projection.getId()).thenReturn(id);
        when(projection.getName()).thenReturn(name);
        return projection;
    }
}
