package com.fuma.hiselectors.taskrun.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.security.jwt.JwtTokenProvider;
import com.fuma.hiselectors.taskrun.dto.TaskRunPanelResponse;
import com.fuma.hiselectors.taskrun.dto.TaskRunResponse;
import com.fuma.hiselectors.taskrun.model.TaskRunStatus;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import com.fuma.hiselectors.taskrun.service.TaskRunQueryService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:task-run-controller;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "jwt.secret=task-run-controller-test-secret-task-run-controller-test-secret",
        "jwt.access-token-validity-seconds=3600",
        "discovery.defaults.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskRunAdminControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-23T03:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private TaskRunAdminController controller;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    @MockitoBean private TaskRunQueryService taskRunQueryService;

    @Test
    void adminCanReadPanelWithExactlyThePublicTaskRunFields() throws Exception {
        TaskRunResponse item = response();
        when(taskRunQueryService.getPanel())
                .thenReturn(new TaskRunPanelResponse(List.of(item), NOW));

        String body = mockMvc.perform(get("/api/admin/task-runs/panel")
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.serverTime").value("2026-08-23T03:00:00Z"))
                .andExpect(jsonPath("$.data.items[0].progressPercent").value(50))
                .andExpect(jsonPath("$.data.items[0].startedBy.adminId").value(7))
                .andExpect(jsonPath("$.data.items[0].startedBy.name").value("관리자"))
                .andReturn().getResponse().getContentAsString();

        JsonNode taskRun = objectMapper.readTree(body).at("/data/items/0");
        assertThat(taskRun.properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrderElementsOf(Set.of(
                        "runId", "taskType", "triggerType", "status", "currentStep",
                        "progressMessage",
                        "totalCount", "processedCount", "succeededCount", "failedCount",
                        "skippedCount", "progressPercent", "startedBy", "startedAt", "finishedAt"));
    }

    @Test
    void recentForwardsPagingAndDetailReturnsTaskRun() throws Exception {
        TaskRunResponse response = response();
        PageRequest pageable = PageRequest.of(2, 5);
        when(taskRunQueryService.getRecent(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response, response), pageable, 12));
        when(taskRunQueryService.getDetail(response.runId())).thenReturn(response);

        mockMvc.perform(get("/api/admin/task-runs/recent")
                        .header("Authorization", bearer("ADMIN"))
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.number").value(2))
                .andExpect(jsonPath("$.data.size").value(5))
                .andExpect(jsonPath("$.data.totalElements").value(12));
        mockMvc.perform(get("/api/admin/task-runs/{runId}", response.runId())
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value(response.runId().toString()));

        verify(taskRunQueryService).getRecent(any(Pageable.class));
        verify(taskRunQueryService).getDetail(response.runId());
    }

    @Test
    void recentCapsOversizedPagesAtOneHundred() throws Exception {
        when(taskRunQueryService.getRecent(any(Pageable.class)))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/admin/task-runs/recent")
                        .header("Authorization", bearer("ADMIN"))
                        .param("size", "1000000"))
                .andExpect(status().isOk());

        verify(taskRunQueryService).getRecent(
                org.mockito.ArgumentMatchers.argThat(pageable ->
                        pageable.isPaged() && pageable.getPageSize() == 100));
    }

    @Test
    void recentNormalizesUnpagedRequestsToTheDefaultPageSize() {
        when(taskRunQueryService.getRecent(any(Pageable.class)))
                .thenReturn(Page.empty());

        controller.getRecent(Pageable.unpaged());

        verify(taskRunQueryService).getRecent(PageRequest.of(0, 20));
    }

    @Test
    void taskRunReadEndpointsRequireAdminRole() throws Exception {
        for (String path : List.of(
                "/api/admin/task-runs/panel",
                "/api/admin/task-runs/recent",
                "/api/admin/task-runs/" + UUID.randomUUID())) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(path).header("Authorization", bearer("USER")))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void detailReturnsTaskRunNotFound() throws Exception {
        UUID runId = UUID.randomUUID();
        when(taskRunQueryService.getDetail(runId))
                .thenThrow(new BusinessException(ErrorCode.TASK_RUN_NOT_FOUND));

        mockMvc.perform(get("/api/admin/task-runs/{runId}", runId)
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TASK_RUN_NOT_FOUND"));
    }

    private String bearer(String role) {
        return "Bearer " + jwtTokenProvider.createToken("test", role);
    }

    private TaskRunResponse response() {
        return new TaskRunResponse(
                UUID.randomUUID(),
                TaskType.CONTENT_SYNC,
                TriggerType.ADMIN_TRIGGERED,
                TaskRunStatus.RUNNING,
                "콘텐츠 조회",
                "크리에이터 5명 수집",
                10L,
                5L,
                4L,
                1L,
                0L,
                50,
                new TaskRunResponse.StartedBy(7L, "관리자"),
                NOW.minusSeconds(30),
                null);
    }
}
