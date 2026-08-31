package com.fuma.hiselectors.taskrun.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.admin.repository.AdminRepository;
import com.fuma.hiselectors.content.service.ContentBatchService;
import com.fuma.hiselectors.content.task.ContentSyncTask;
import com.fuma.hiselectors.creator.task.CreatorSyncTask;
import com.fuma.hiselectors.creator.task.InstagramCreatorSyncTask;
import com.fuma.hiselectors.inspection.task.ContentReportGenerationTask;
import com.fuma.hiselectors.notification.task.KakaoMessageSendTask;
import com.fuma.hiselectors.proposal.dto.ProposalCreateRequest;
import com.fuma.hiselectors.proposal.task.ProposalEmailTask;
import com.fuma.hiselectors.proposal.task.ProposalEmailTaskFactory;
import com.fuma.hiselectors.settlement.task.SettlementEstimateTask;
import com.fuma.hiselectors.settlement.task.SettlementFinalizationTask;
import com.fuma.hiselectors.settlement.task.SettlementRecalculationTask;
import com.fuma.hiselectors.settlement.task.SettlementRecalculationTaskFactory;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskRunStatus;
import com.fuma.hiselectors.taskrun.model.TaskType;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.time.YearMonth;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.ObjectMapper;

class TaskRunTaskResolverTest {

    private static ValidatorFactory validators;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AdminRepository admins = mock(AdminRepository.class);
    private final CreatorSyncTask creatorSync = mock(CreatorSyncTask.class);
    private final InstagramCreatorSyncTask instagramSync = mock(InstagramCreatorSyncTask.class);
    private final ContentSyncTask contentSync = mock(ContentSyncTask.class);
    private final ContentReportGenerationTask contentReports = mock(ContentReportGenerationTask.class);
    private final SettlementEstimateTask estimates = mock(SettlementEstimateTask.class);
    private final SettlementFinalizationTask finalization = mock(SettlementFinalizationTask.class);
    private final SettlementRecalculationTaskFactory recalculation =
            mock(SettlementRecalculationTaskFactory.class);
    private final KakaoMessageSendTask kakao = mock(KakaoMessageSendTask.class);
    private final ProposalEmailTaskFactory proposals = mock(ProposalEmailTaskFactory.class);
    private TaskRunTaskResolver resolver;

    @BeforeAll
    static void createValidator() {
        validators = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidator() {
        validators.close();
    }

    @BeforeEach
    void setUp() {
        resolver = resolverFor(contentSync);
    }

    private TaskRunTaskResolver resolverFor(ContentSyncTask contentTask) {
        return new TaskRunTaskResolver(
                objectMapper, validators.getValidator(), admins,
                provider(creatorSync), provider(instagramSync), provider(contentTask),
                provider(contentReports), provider(estimates), provider(finalization),
                provider(recalculation), provider(kakao), provider(proposals));
    }

    @Test
    void restoresWholeCreatorSourcesAndContentTaskIncludingItsTerminalHook() {
        assertThat(resolver.resolve(run(TaskType.CREATOR_SYNC, "{\"source\":\"youtube\"}")))
                .isSameAs(creatorSync);
        assertThat(resolver.resolve(run(TaskType.CREATOR_SYNC, "{\"source\":\"instagram\"}")))
                .isSameAs(instagramSync);
        assertThat(resolver.resolve(run(TaskType.CONTENT_SYNC, "{}"))).isSameAs(contentSync);
        assertThat(resolver.resolve(run(TaskType.CONTENT_REPORT_GENERATION,
                "{\"sourceContentSyncRunId\":\"4a4c42f2-b8a1-4fc0-8981-519fd50de85c\"}")))
                .isSameAs(contentReports);
        verifyNoInteractions(creatorSync, instagramSync, contentSync, contentReports);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void restoresExplicitContentModeUsingTheOriginalDomainTasks(boolean fastMode) {
        TrackedTask fastSync = mock(TrackedTask.class);
        TrackedTask fastReport = mock(TrackedTask.class);
        when(contentSync.fastModeTask()).thenReturn(fastSync);
        when(contentReports.fastModeTask()).thenReturn(fastReport);

        assertThat(resolver.resolve(run(TaskType.CONTENT_SYNC,
                "{\"fastMode\":" + fastMode + "}")))
                .isSameAs(fastMode ? fastSync : contentSync);
        assertThat(resolver.resolve(run(TaskType.CONTENT_REPORT_GENERATION,
                "{\"sourceContentSyncRunId\":\"4a4c42f2-b8a1-4fc0-8981-519fd50de85c\","
                        + "\"fastMode\":" + fastMode + "}")))
                .isSameAs(fastMode ? fastReport : contentReports);

        verifyNoInteractions(fastSync, fastReport, admins);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"true\"", "\"false\"", "1", "0", "{}", "[]"})
    void malformedContentModeCannotResolveOrBroadenATerminalReplay(String fastMode) {
        TaskRun sync = run(TaskType.CONTENT_SYNC, "{\"fastMode\":" + fastMode + "}");
        TaskRun report = run(TaskType.CONTENT_REPORT_GENERATION,
                "{\"sourceContentSyncRunId\":\"4a4c42f2-b8a1-4fc0-8981-519fd50de85c\","
                        + "\"fastMode\":" + fastMode + "}");

        assertThatThrownBy(() -> resolver.resolve(sync))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolver.afterTerminal(sync))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolver.resolve(report))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(contentSync, contentReports, admins);
    }

    @ParameterizedTest
    @EnumSource(value = TaskRunStatus.class, names = {"SUCCEEDED", "PARTIAL_FAILED", "FAILED"})
    void fastCompletionAndRedeliveryPreserveChildScopeAndIdempotency(TaskRunStatus status) {
        UUID runId = UUID.fromString("97f0a053-2bde-4932-b8e4-58f3f98236c1");
        ContentBatchService batch = mock(ContentBatchService.class);
        TaskRunExecutionService submissions = mock(TaskRunExecutionService.class);
        TrackedTask fastReport = mock(TrackedTask.class);
        when(contentReports.fastModeTask()).thenReturn(fastReport);
        resolver = resolverFor(new ContentSyncTask(batch, submissions, contentReports, objectMapper));
        TaskRun sync = run(TaskType.CONTENT_SYNC, "{\"fastMode\":true}");
        when(sync.getRunId()).thenReturn(runId);
        when(sync.getStatus()).thenReturn(status);

        resolver.resolve(sync).afterTerminal(new TaskTerminalContext(runId, status));
        resolver.afterTerminal(sync);

        ArgumentCaptor<TaskStartCommand> commands = ArgumentCaptor.forClass(TaskStartCommand.class);
        verify(submissions, times(2)).submit(commands.capture(), same(fastReport));
        TaskStartCommand first = commands.getAllValues().getFirst();
        TaskStartCommand replay = commands.getAllValues().getLast();
        assertThat(first.taskType()).isEqualTo(TaskType.CONTENT_REPORT_GENERATION);
        assertThat(first.businessPayload()).isEqualTo(objectMapper.createObjectNode()
                .put("sourceContentSyncRunId", runId.toString()).put("fastMode", true));
        assertThat(replay.businessPayload()).isEqualTo(first.businessPayload());
        assertThat(replay.idempotencyKey()).isEqualTo(first.idempotencyKey());
        assertThat(resolver.resolve(run(TaskType.CONTENT_REPORT_GENERATION,
                objectMapper.writeValueAsString(replay.businessPayload())))).isSameAs(fastReport);
        verifyNoInteractions(batch, admins, fastReport);
    }

    @Test
    void restoresTestAndCategoryExecutionWithoutBroadeningItsScope() throws Exception {
        TaskExecutionContext context = mock(TaskExecutionContext.class);

        resolver.resolve(run(TaskType.CREATOR_SYNC, "{\"source\":\"youtube-test\"}"))
                .execute(context);
        resolver.resolve(run(TaskType.CREATOR_SYNC,
                "{\"source\":\"youtube-category\",\"categoryId\":42}"))
                .execute(context);

        verify(creatorSync).executeTest(context);
        verify(creatorSync).executeCategory(context, 42L);
    }

    @Test
    void restoresScheduledSettlementModes() {
        assertThat(resolver.resolve(run(TaskType.SETTLEMENT_CALCULATION,
                "{\"mode\":\"ESTIMATE\"}"))).isSameAs(estimates);
        assertThat(resolver.resolve(run(TaskType.SETTLEMENT_CALCULATION,
                "{\"mode\":\"FINALIZE\"}"))).isSameAs(finalization);
    }

    @Test
    void restoresExactMonthSelectorAndForceForRecalculation() {
        SettlementRecalculationTask task = mock(SettlementRecalculationTask.class);
        when(recalculation.create(YearMonth.of(2026, 7), 9L, true)).thenReturn(task);

        assertThat(resolver.resolve(run(TaskType.SETTLEMENT_CALCULATION,
                "{\"activityMonth\":\"2026-07\",\"selectorsId\":9,\"force\":true}")))
                .isSameAs(task);

        verify(recalculation).create(YearMonth.of(2026, 7), 9L, true);
    }

    @Test
    void preservesExplicitNullScopeForAllMonthsAndAllSelectors() {
        SettlementRecalculationTask task = mock(SettlementRecalculationTask.class);
        when(recalculation.create(null, null, false)).thenReturn(task);

        assertThat(resolver.resolve(run(TaskType.SETTLEMENT_CALCULATION,
                "{\"activityMonth\":null,\"selectorsId\":null,\"force\":false}")))
                .isSameAs(task);

        verify(recalculation).create(null, null, false);
    }

    @Test
    void resolvesNotificationIdWithCurrentLoginOfPersistedInitiatingAdmin() {
        currentAdmin();
        TrackedTask task = mock(TrackedTask.class);
        when(kakao.resend("renamed-admin", 17L)).thenReturn(task);

        assertThat(resolver.resolve(run(TaskType.KAKAO_MESSAGE_SEND,
                "{\"notificationId\":17}"))).isSameAs(task);

        verify(admins).findById(3L);
        verify(kakao).resend("renamed-admin", 17L);
    }

    @Test
    void restoresProposalCustomContentAndDefaultTemplateWithoutChangingRequest() {
        currentAdmin();
        ProposalCreateRequest custom = new ProposalCreateRequest(7L, "제안 제목", "첫 줄\n둘째 줄");
        ProposalCreateRequest defaultTemplate = new ProposalCreateRequest(7L);
        ProposalEmailTask customTask = mock(ProposalEmailTask.class);
        ProposalEmailTask defaultTask = mock(ProposalEmailTask.class);
        when(proposals.create("renamed-admin", custom)).thenReturn(customTask);
        when(proposals.create("renamed-admin", defaultTemplate)).thenReturn(defaultTask);

        assertThat(resolver.resolve(run(TaskType.PROPOSAL_EMAIL_SEND,
                proposalPayload(custom)))).isSameAs(customTask);
        assertThat(resolver.resolve(run(TaskType.PROPOSAL_EMAIL_SEND,
                proposalPayload(defaultTemplate)))).isSameAs(defaultTask);

        verify(proposals).create("renamed-admin", custom);
        verify(proposals).create("renamed-admin", defaultTemplate);
    }

    @ParameterizedTest
    @MethodSource("invalidPayloads")
    void rejectsMissingUnknownOrMalformedScopeBeforeAnyDomainWork(TaskType type, String payload) {
        assertThatThrownBy(() -> resolver.resolve(run(type, payload)))
                .isInstanceOf(IllegalArgumentException.class);
        if (type == TaskType.CONTENT_SYNC) {
            assertThatThrownBy(() -> resolver.afterTerminal(run(type, payload)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        verifyNoInteractions(admins, creatorSync, instagramSync, contentSync, contentReports,
                estimates, finalization, recalculation, kakao, proposals);
    }

    static Stream<Arguments> invalidPayloads() {
        return Stream.of(
                Arguments.of(TaskType.CONTENT_SYNC, null),
                Arguments.of(TaskType.CONTENT_SYNC, " "),
                Arguments.of(TaskType.CONTENT_SYNC, "{"),
                Arguments.of(TaskType.CONTENT_SYNC, "null"),
                Arguments.of(TaskType.CONTENT_SYNC, "[]"),
                Arguments.of(TaskType.CONTENT_SYNC, "{\"selectedIds\":[1]}"),
                Arguments.of(TaskType.CONTENT_SYNC, "{\"fastMode\":true,\"selectedIds\":[1]}"),
                Arguments.of(TaskType.CREATOR_SYNC, "{}"),
                Arguments.of(TaskType.CREATOR_SYNC, "{\"source\":\"unknown\"}"),
                Arguments.of(TaskType.CREATOR_SYNC, "{\"source\":\"youtube-category\"}"),
                Arguments.of(TaskType.CREATOR_SYNC,
                        "{\"source\":\"youtube\",\"categoryId\":3}"),
                Arguments.of(TaskType.CREATOR_SYNC,
                        "{\"source\":\"youtube-category\",\"categoryId\":null}"),
                Arguments.of(TaskType.CREATOR_SYNC,
                        "{\"source\":\"youtube-category\",\"categoryId\":\"3\"}"),
                Arguments.of(TaskType.CREATOR_SYNC,
                        "{\"source\":\"youtube-category\",\"categoryId\":1.5}"),
                Arguments.of(TaskType.CONTENT_REPORT_GENERATION,
                        "{\"sourceContentSyncRunId\":\"not-a-uuid\"}"),
                Arguments.of(TaskType.CONTENT_REPORT_GENERATION, "{\"fastMode\":true}"),
                Arguments.of(TaskType.CONTENT_REPORT_GENERATION,
                        "{\"sourceContentSyncRunId\":\"4a4c42f2-b8a1-4fc0-8981-519fd50de85c\","
                                + "\"fastMode\":true,\"selectedIds\":[1]}"),
                Arguments.of(TaskType.SETTLEMENT_CALCULATION, "{}"),
                Arguments.of(TaskType.SETTLEMENT_CALCULATION, "{\"mode\":\"UNKNOWN\"}"),
                Arguments.of(TaskType.SETTLEMENT_CALCULATION,
                        "{\"mode\":\"ESTIMATE\",\"selectorsId\":3}"),
                Arguments.of(TaskType.SETTLEMENT_CALCULATION,
                        "{\"activityMonth\":\"2026-13\",\"selectorsId\":null,\"force\":false}"),
                Arguments.of(TaskType.SETTLEMENT_CALCULATION,
                        "{\"activityMonth\":null,\"selectorsId\":null,\"force\":\"false\"}"),
                Arguments.of(TaskType.KAKAO_MESSAGE_SEND, "{\"notificationId\":0}"),
                Arguments.of(TaskType.KAKAO_MESSAGE_SEND,
                        "{\"notificationId\":9223372036854775808}"),
                Arguments.of(TaskType.PROPOSAL_EMAIL_SEND,
                        "{\"creatorId\":1,\"subject\":\"제목\",\"body\":null}"),
                Arguments.of(TaskType.PROPOSAL_EMAIL_SEND,
                        "{\"creatorId\":1,\"subject\":\"줄바꿈\\n제목\",\"body\":\"본문\"}"),
                Arguments.of(TaskType.PROPOSAL_EMAIL_SEND,
                        "{\"creatorId\":1,\"subject\":3,\"body\":\"본문\"}"),
                Arguments.of(TaskType.APPLICATION_REPORT_GENERATION, "{}"));
    }

    @Test
    void doesNotSendWithoutInitiatingAdminOrIfThatAdminWasRemoved() {
        TaskRun missingAdmin = run(TaskType.KAKAO_MESSAGE_SEND, "{\"notificationId\":17}");
        when(missingAdmin.getStartedByAdminId()).thenReturn(null);
        assertThatThrownBy(() -> resolver.resolve(missingAdmin))
                .isInstanceOf(IllegalArgumentException.class);

        when(admins.findById(3L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> resolver.resolve(
                run(TaskType.PROPOSAL_EMAIL_SEND, proposalPayload(new ProposalCreateRequest(7L)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Task initiating admin is unavailable");
        verifyNoInteractions(kakao, proposals);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" \t"})
    void unusableAdminLoginIsPermanentCommandFailure(String loginId) {
        when(admins.findById(3L)).thenReturn(Optional.of(
                Admin.builder().loginId(loginId).role("ADMIN").build()));

        assertThatThrownBy(() -> resolver.resolve(
                run(TaskType.KAKAO_MESSAGE_SEND, "{\"notificationId\":17}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Task initiating admin is unavailable");
        verifyNoInteractions(kakao, proposals);
    }

    @Test
    void databaseOutageIsNotReclassifiedAsPermanentAdminFailure() {
        DataAccessResourceFailureException unavailable =
                new DataAccessResourceFailureException("database unavailable");
        when(admins.findById(3L)).thenThrow(unavailable);

        assertThatThrownBy(() -> resolver.resolve(
                run(TaskType.KAKAO_MESSAGE_SEND, "{\"notificationId\":17}")))
                .isSameAs(unavailable);
        verifyNoInteractions(kakao, proposals);
    }

    @ParameterizedTest
    @EnumSource(TaskType.class)
    void automaticRetryIsLimitedToTheTwoApprovedBatchTypes(TaskType type) {
        assertThat(resolver.automaticRetrySafe(type)).isEqualTo(Set.of(
                TaskType.CREATOR_SYNC, TaskType.CONTENT_SYNC)
                .contains(type));
    }

    @ParameterizedTest
    @EnumSource(TaskType.class)
    void terminalReplayRestoresOnlyContentScopeWithoutAdminLookup(TaskType type) {
        UUID runId = UUID.fromString("97f0a053-2bde-4932-b8e4-58f3f98236c1");
        TaskRun run = run(type, type == TaskType.CONTENT_SYNC ? "{}" : null);
        when(run.getRunId()).thenReturn(runId);
        when(run.getStatus()).thenReturn(TaskRunStatus.SUCCEEDED);

        resolver.afterTerminal(run);

        if (type == TaskType.CONTENT_SYNC) {
            verify(contentSync).afterTerminal(new TaskTerminalContext(runId, TaskRunStatus.SUCCEEDED));
            verify(run).getBusinessPayload();
        } else {
            verifyNoInteractions(contentSync);
            verify(run, never()).getBusinessPayload();
        }
        verify(run, never()).getStartedByAdminId();
        verifyNoInteractions(admins, creatorSync, instagramSync, contentReports,
                estimates, finalization, recalculation, kakao, proposals);
    }

    private void currentAdmin() {
        when(admins.findById(3L)).thenReturn(Optional.of(
                Admin.builder().loginId("renamed-admin").role("ADMIN").build()));
    }

    private String proposalPayload(ProposalCreateRequest request) {
        return objectMapper.writeValueAsString(objectMapper.createObjectNode()
                .put("creatorId", request.creatorId())
                .put("subject", request.subject())
                .put("body", request.body()));
    }

    private TaskRun run(TaskType type, String payload) {
        TaskRun run = mock(TaskRun.class);
        when(run.getTaskType()).thenReturn(type);
        when(run.getBusinessPayload()).thenReturn(payload);
        when(run.getStartedByAdminId()).thenReturn(3L);
        return run;
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(value);
        return provider;
    }
}
