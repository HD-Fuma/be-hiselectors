package com.fuma.hiselectors.taskrun.queue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.admin.repository.AdminRepository;
import com.fuma.hiselectors.config.SecurityConfig;
import com.fuma.hiselectors.security.jwt.JwtAccessDeniedHandler;
import com.fuma.hiselectors.security.jwt.JwtAuthenticationEntryPoint;
import com.fuma.hiselectors.security.jwt.JwtTokenProvider;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import com.fuma.hiselectors.taskrun.service.TaskRunExecutionService;
import com.fuma.hiselectors.taskrun.service.TaskRunTaskResolver;
import com.fuma.hiselectors.taskrun.service.TaskStartResult;
import com.fuma.hiselectors.taskrun.service.TrackedTask;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TaskQueueRetryController.class, properties = {
        "task-queue.enabled=true",
        "jwt.secret=task-queue-retry-security-test-secret-task-queue-retry-security-test-secret",
        "jwt.access-token-validity-seconds=3600"
})
@Import({SecurityConfig.class, JwtTokenProvider.class, JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class, TaskQueueRetrySecurityContractTest.SecurityTestConfiguration.class})
@ActiveProfiles("test")
class TaskQueueRetrySecurityContractTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class SecurityTestConfiguration { }

    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenProvider jwt;
    @MockitoBean private TaskQueueState state;
    @MockitoBean private TaskRunTaskResolver resolver;
    @MockitoBean private TaskRunExecutionService execution;
    @MockitoBean private AdminRepository adminRepository;
    @MockitoBean private CacheManager cacheManager;

    @Test
    void anonymousCannotRequestRetry() throws Exception {
        mvc.perform(post("/api/admin/task-runs/{runId}/retry", UUID.randomUUID())
                        .header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(state, execution, adminRepository);
    }

    @Test
    void authenticatedUserCannotRequestRetry() throws Exception {
        mvc.perform(post("/api/admin/task-runs/{runId}/retry", UUID.randomUUID())
                        .header("Idempotency-Key", UUID.randomUUID())
                        .header("Authorization", "Bearer " + jwt.createToken("user-login", "USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verifyNoInteractions(state, execution, adminRepository);
    }

    @Test
    void adminTokenSuppliesPrincipalAndUsesExistingAdminRecord() throws Exception {
        Instant now = Instant.parse("2026-08-31T00:00:00Z");
        TaskRun source = TaskRun.queued(TaskType.CONTENT_SYNC, TriggerType.SCHEDULED, null,
                UUID.randomUUID(), "original", TaskType.CONTENT_SYNC.name(), now);
        source.enableQueue("{}", now);
        source.fail("TEST_FAILURE", "safe summary", now);
        TaskRun retry = TaskRun.queued(TaskType.CONTENT_SYNC, TriggerType.ADMIN_TRIGGERED, 7L,
                UUID.randomUUID(), "retry", TaskType.CONTENT_SYNC.name(), now);
        Admin admin = mock(Admin.class);
        when(admin.getId()).thenReturn(7L);
        when(admin.getName()).thenReturn("관리자");
        when(adminRepository.findByLoginId("current-admin")).thenReturn(Optional.of(admin));
        when(state.get(source.getRunId())).thenReturn(source);
        when(resolver.automaticRetrySafe(TaskType.CONTENT_SYNC)).thenReturn(true);
        when(resolver.resolve(source)).thenReturn(mock(TrackedTask.class));
        when(execution.submit(any(), any())).thenReturn(new TaskStartResult.Created(retry));

        mvc.perform(post("/api/admin/task-runs/{runId}/retry", source.getRunId())
                        .header("Idempotency-Key", retry.getIdempotencyKey())
                        .header("Authorization", "Bearer " + jwt.createToken("current-admin", "ADMIN")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.runId").value(retry.getRunId().toString()))
                .andExpect(jsonPath("$.data.startedBy.adminId").value(7));
        verify(adminRepository).findByLoginId("current-admin");
    }
}
