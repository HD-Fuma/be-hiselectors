package com.fuma.hiselectors.creator.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.creator.discovery.DiscoveryPipelineService;
import com.fuma.hiselectors.creator.discovery.InstagramDiscoveryService;
import com.fuma.hiselectors.common.ApiResultAdvice;
import com.fuma.hiselectors.creator.discovery.dto.InstagramDiscoveryResult;
import com.fuma.hiselectors.creator.task.CreatorSyncTask;
import com.fuma.hiselectors.creator.task.InstagramCreatorSyncTask;
import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.admin.repository.AdminRepository;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import com.fuma.hiselectors.taskrun.service.TaskExecutionContext;
import com.fuma.hiselectors.taskrun.service.TaskRunExecutionService;
import com.fuma.hiselectors.taskrun.service.TaskStartResult;
import com.fuma.hiselectors.taskrun.service.TrackedTask;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

class DiscoveryAdminControllerTest {

    private InstagramDiscoveryService instagramDiscoveryService;
    private TaskRunExecutionService taskRunExecutionService;
    private AdminRepository adminRepository;
    private CreatorSyncTask creatorSyncTask;
    private InstagramCreatorSyncTask instagramCreatorSyncTask;
    private Admin admin;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DiscoveryPipelineService discoveryPipelineService =
                mock(DiscoveryPipelineService.class);
        instagramDiscoveryService = mock(InstagramDiscoveryService.class);
        taskRunExecutionService = mock(TaskRunExecutionService.class);
        adminRepository = mock(AdminRepository.class);
        creatorSyncTask = mock(CreatorSyncTask.class);
        instagramCreatorSyncTask = mock(InstagramCreatorSyncTask.class);
        admin = mock(Admin.class);
        when(admin.getId()).thenReturn(7L);
        when(admin.getName()).thenReturn("관리자");
        when(adminRepository.findByLoginId("admin")).thenReturn(Optional.of(admin));
        mockMvc = MockMvcBuilders.standaloneSetup(new DiscoveryAdminController(
                        discoveryPipelineService,
                        instagramDiscoveryService,
                        taskRunExecutionService,
                        creatorSyncTask,
                        instagramCreatorSyncTask,
                        adminRepository,
                        new ObjectMapper()
                ))
                .setControllerAdvice(new GlobalExceptionHandler(), new ApiResultAdvice())
                .build();
    }

    @Test
    void runsYoutubeDiscoveryBatch() throws Exception {
        UUID key = UUID.randomUUID();
        when(taskRunExecutionService.submit(org.mockito.ArgumentMatchers.any(),
                org.mockito.Mockito.same(creatorSyncTask)))
                .thenReturn(new TaskStartResult.Created(run(TaskType.CREATOR_SYNC, key)));

        mockMvc.perform(post("/api/admin/discovery/youtube/run")
                        .header("Idempotency-Key", key)
                        .principal(() -> "admin"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.taskType").value("CREATOR_SYNC"))
                .andExpect(jsonPath("$.data.status").value("QUEUED"));

        verify(taskRunExecutionService).submit(org.mockito.ArgumentMatchers.argThat(command ->
                        command.idempotencyKey().equals(key)
                                && command.startedByAdminId().equals(7L)
                                && command.businessPayload().get("source").asText().equals("youtube")),
                org.mockito.Mockito.same(creatorSyncTask));
    }

    @Test
    void runsQuickYoutubeDiscoveryBatch() throws Exception {
        UUID key = UUID.randomUUID();
        when(taskRunExecutionService.submit(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new TaskStartResult.Created(run(TaskType.CREATOR_SYNC, key)));

        mockMvc.perform(post("/api/admin/discovery/youtube/run")
                        .param("test", "true")
                        .header("Idempotency-Key", key)
                        .principal(() -> "admin"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("QUEUED"));

        ArgumentCaptor<TrackedTask> task = ArgumentCaptor.forClass(TrackedTask.class);
        verify(taskRunExecutionService).submit(org.mockito.ArgumentMatchers.argThat(command ->
                        command.idempotencyKey().equals(key)
                                && command.startedByAdminId().equals(7L)
                                && command.businessPayload().get("source").asText()
                                .equals("youtube-test")),
                task.capture());
        TaskExecutionContext context = mock(TaskExecutionContext.class);
        task.getValue().execute(context);
        verify(creatorSyncTask).executeTest(context);
    }

    @Test
    void runsInstagramDiscoveryBatch() throws Exception {
        UUID key = UUID.randomUUID();
        when(taskRunExecutionService.submit(org.mockito.ArgumentMatchers.any(),
                org.mockito.Mockito.same(instagramCreatorSyncTask)))
                .thenReturn(new TaskStartResult.Replayed(run(TaskType.CREATOR_SYNC, key)));

        mockMvc.perform(post("/api/admin/discovery/instagram/run")
                        .header("Idempotency-Key", key)
                        .principal(() -> "admin"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.taskType").value("CREATOR_SYNC"));
    }

    @Test
    void discoversInstagramCreatorFromYoutubeCreator() throws Exception {
        InstagramDiscoveryResult result = new InstagramDiscoveryResult(
                10L,
                20L,
                "nike",
                true,
                291_530_362L,
                1_668L,
                new BigDecimal("0.04"),
                LocalDateTime.of(2026, 8, 12, 2, 0, 58)
        );
        when(instagramDiscoveryService.discoverFromYoutubeCreator(10L))
                .thenReturn(result);

        mockMvc.perform(post("/api/admin/discovery/creators/10/instagram")
                        .principal(() -> "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sourceCreatorId").value(10))
                .andExpect(jsonPath("$.data.instagramCreatorId").value(20))
                .andExpect(jsonPath("$.data.username").value("nike"))
                .andExpect(jsonPath("$.data.created").value(true))
                .andExpect(jsonPath("$.data.followerCount").value(291_530_362))
                .andExpect(jsonPath("$.data.mediaCount").value(1_668))
                .andExpect(jsonPath("$.data.engagementRate").value(0.04));

        verify(instagramDiscoveryService).discoverFromYoutubeCreator(10L);
    }

    @Test
    void returnsNotFoundWhenInstagramUsernameIsMissing() throws Exception {
        when(instagramDiscoveryService.discoverFromYoutubeCreator(10L))
                .thenThrow(new BusinessException(ErrorCode.INSTAGRAM_HANDLE_NOT_FOUND));

        mockMvc.perform(post("/api/admin/discovery/creators/10/instagram")
                        .principal(() -> "admin"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INSTAGRAM_HANDLE_NOT_FOUND"));
    }

    @Test
    void rejectsMissingIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/admin/discovery/youtube/run")
                        .principal(() -> "admin"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsConflictWhenCreatorSyncIsAlreadyActive() throws Exception {
        UUID key = UUID.randomUUID();
        when(taskRunExecutionService.submit(org.mockito.ArgumentMatchers.any(),
                org.mockito.Mockito.same(creatorSyncTask)))
                .thenReturn(new TaskStartResult.ActiveConflict(run(TaskType.CREATOR_SYNC, key)));

        mockMvc.perform(post("/api/admin/discovery/youtube/run")
                        .header("Idempotency-Key", key)
                        .principal(() -> "admin"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_ALREADY_RUNNING"));
    }

    private TaskRun run(TaskType type, UUID key) {
        return TaskRun.queued(type, TriggerType.ADMIN_TRIGGERED, 7L, key,
                "fingerprint", type.name(), Instant.parse("2026-08-24T00:00:00Z"));
    }
}
