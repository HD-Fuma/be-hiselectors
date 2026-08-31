package com.fuma.hiselectors.taskrun.service;

import com.fuma.hiselectors.admin.model.Admin;
import com.fuma.hiselectors.admin.repository.AdminRepository;
import com.fuma.hiselectors.content.task.ContentSyncTask;
import com.fuma.hiselectors.creator.task.CreatorSyncTask;
import com.fuma.hiselectors.creator.task.InstagramCreatorSyncTask;
import com.fuma.hiselectors.inspection.task.ContentReportGenerationTask;
import com.fuma.hiselectors.notification.task.KakaoMessageSendTask;
import com.fuma.hiselectors.proposal.dto.ProposalCreateRequest;
import com.fuma.hiselectors.proposal.task.ProposalEmailTaskFactory;
import com.fuma.hiselectors.settlement.task.SettlementEstimateTask;
import com.fuma.hiselectors.settlement.task.SettlementFinalizationTask;
import com.fuma.hiselectors.settlement.task.SettlementRecalculationTaskFactory;
import com.fuma.hiselectors.taskrun.model.TaskRun;
import com.fuma.hiselectors.taskrun.model.TaskType;
import jakarta.validation.Validator;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 저장된 실행 인자만으로 기존 도메인 작업을 복원한다. 알 수 없는 범위는 전체 실행으로 대체하지 않는다. */
@Component
@RequiredArgsConstructor
public class TaskRunTaskResolver {

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final AdminRepository adminRepository;
    private final ObjectProvider<CreatorSyncTask> creatorSyncTask;
    private final ObjectProvider<InstagramCreatorSyncTask> instagramCreatorSyncTask;
    private final ObjectProvider<ContentSyncTask> contentSyncTask;
    private final ObjectProvider<ContentReportGenerationTask> contentReportGenerationTask;
    private final ObjectProvider<SettlementEstimateTask> settlementEstimateTask;
    private final ObjectProvider<SettlementFinalizationTask> settlementFinalizationTask;
    private final ObjectProvider<SettlementRecalculationTaskFactory> settlementRecalculationTaskFactory;
    private final ObjectProvider<KakaoMessageSendTask> kakaoMessageSendTask;
    private final ObjectProvider<ProposalEmailTaskFactory> proposalEmailTaskFactory;

    public TrackedTask resolve(TaskRun run) {
        JsonNode payload = payload(run);
        return switch (run.getTaskType()) {
            case CREATOR_SYNC -> creatorSync(payload);
            case CONTENT_SYNC -> {
                boolean fastMode = contentFastMode(payload);
                ContentSyncTask task = contentSyncTask.getObject();
                yield fastMode ? task.fastModeTask() : task;
            }
            case CONTENT_REPORT_GENERATION -> {
                boolean fastMode = contentFastMode(payload, "sourceContentSyncRunId");
                UUID.fromString(requiredString(payload, "sourceContentSyncRunId"));
                ContentReportGenerationTask task = contentReportGenerationTask.getObject();
                yield fastMode ? task.fastModeTask() : task;
            }
            case SETTLEMENT_CALCULATION -> settlement(payload);
            case KAKAO_MESSAGE_SEND -> {
                requireFields(payload, "notificationId");
                Long notificationId = requiredId(payload, "notificationId");
                yield kakaoMessageSendTask.getObject().resend(adminLoginId(run), notificationId);
            }
            case PROPOSAL_EMAIL_SEND -> proposal(run, payload);
            // 이 타입은 아직 TaskRun 생산자가 없다. 지원자 분석의 기존 전용 SQS/DB lease를 유지한다.
            case APPLICATION_REPORT_GENERATION -> throw new IllegalArgumentException(
                    "Application reports use the separate analysis worker, not TaskRun dispatch");
        };
    }

    /** 실행 전체의 제한된 자동 재시도 허용 여부이며, 개별 외부 호출의 exactly-once 보장은 아니다. */
    public boolean automaticRetrySafe(TaskType taskType) {
        return switch (taskType) {
            case CREATOR_SYNC, CONTENT_SYNC -> true;
            // 리포트 중단 시 남은 INSPECTING 항목은 별도 도메인 복구 없이는 다시 선택되지 않는다.
            case CONTENT_REPORT_GENERATION, APPLICATION_REPORT_GENERATION, SETTLEMENT_CALCULATION,
                    KAKAO_MESSAGE_SEND, PROPOSAL_EMAIL_SEND -> false;
        };
    }

    /** 완료 메시지 재전달도 저장된 콘텐츠 범위를 유지하며 관리자 계정은 재조회하지 않는다. */
    public void afterTerminal(TaskRun run) {
        if (run.getTaskType() == TaskType.CONTENT_SYNC) {
            resolve(run).afterTerminal(
                    new TaskTerminalContext(run.getRunId(), run.getStatus()));
        }
    }

    private boolean contentFastMode(JsonNode payload, String... requiredFields) {
        Set<String> fields = new HashSet<>(payload.propertyNames());
        fields.remove("fastMode");
        if (!fields.equals(Set.of(requiredFields))) {
            throw new IllegalArgumentException("Task payload has missing or unsupported fields");
        }
        JsonNode fastMode = payload.get("fastMode");
        if (fastMode == null) {
            return false;
        }
        if (!fastMode.isBoolean()) {
            throw new IllegalArgumentException("Task payload fastMode must be a boolean");
        }
        return fastMode.booleanValue();
    }

    private TrackedTask creatorSync(JsonNode payload) {
        String source = requiredString(payload, "source");
        if (source.equals("youtube-category")) {
            requireFields(payload, "source", "categoryId");
            Long categoryId = requiredId(payload, "categoryId");
            CreatorSyncTask task = creatorSyncTask.getObject();
            return context -> task.executeCategory(context, categoryId);
        }
        requireFields(payload, "source");
        return switch (source) {
            case "youtube" -> creatorSyncTask.getObject();
            case "youtube-test" -> creatorSyncTask.getObject()::executeTest;
            case "instagram" -> instagramCreatorSyncTask.getObject();
            default -> throw new IllegalArgumentException("Unsupported creator sync source");
        };
    }

    private TrackedTask settlement(JsonNode payload) {
        if (payload.has("mode")) {
            requireFields(payload, "mode");
            return switch (requiredString(payload, "mode")) {
                case "ESTIMATE" -> settlementEstimateTask.getObject();
                case "FINALIZE" -> settlementFinalizationTask.getObject();
                default -> throw new IllegalArgumentException("Unsupported settlement mode");
            };
        }
        // 관리자 재계산의 기존 payload/fingerprint를 변경하지 않고 null=전체 범위를 보존한다.
        requireFields(payload, "activityMonth", "selectorsId", "force");
        String month = nullableString(payload, "activityMonth");
        Long selectorsId = required(payload, "selectorsId").isNull()
                ? null : requiredId(payload, "selectorsId");
        JsonNode force = required(payload, "force");
        if (!force.isBoolean()) {
            throw new IllegalArgumentException("Task payload force must be a boolean");
        }
        YearMonth activityMonth;
        try {
            activityMonth = month == null ? null : YearMonth.parse(month);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Task payload activityMonth must use yyyy-MM");
        }
        return settlementRecalculationTaskFactory.getObject().create(
                activityMonth, selectorsId, force.booleanValue());
    }

    private TrackedTask proposal(TaskRun run, JsonNode payload) {
        requireFields(payload, "creatorId", "subject", "body");
        ProposalCreateRequest request = new ProposalCreateRequest(
                requiredId(payload, "creatorId"),
                nullableString(payload, "subject"),
                nullableString(payload, "body"));
        if (!validator.validate(request).isEmpty()) {
            throw new IllegalArgumentException("Invalid proposal request payload");
        }
        return proposalEmailTaskFactory.getObject().create(adminLoginId(run), request);
    }

    private String adminLoginId(TaskRun run) {
        Long adminId = run.getStartedByAdminId();
        if (adminId == null) {
            throw new IllegalArgumentException("This task requires its initiating admin ID");
        }
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Task initiating admin is unavailable"));
        if (admin.getLoginId() == null || admin.getLoginId().isBlank()) {
            throw new IllegalArgumentException("Task initiating admin is unavailable");
        }
        return admin.getLoginId();
    }

    private JsonNode payload(TaskRun run) {
        String json = run.getBusinessPayload();
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Task payload is missing");
        }
        JsonNode payload;
        try {
            payload = objectMapper.readTree(json);
        } catch (JacksonException exception) {
            // 실행 인자에 메일 본문 등이 포함되므로 파서 원문을 오류 메시지에 남기지 않는다.
            throw new IllegalArgumentException("Task payload is invalid JSON");
        }
        if (payload == null || !payload.isObject()) {
            throw new IllegalArgumentException("Task payload must be a JSON object");
        }
        return payload;
    }

    private void requireFields(JsonNode payload, String... fields) {
        if (!Set.copyOf(payload.propertyNames()).equals(Set.of(fields))) {
            throw new IllegalArgumentException("Task payload has missing or unsupported fields");
        }
    }

    private JsonNode required(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null) {
            throw new IllegalArgumentException("Task payload is missing " + field);
        }
        return value;
    }

    private String requiredString(JsonNode payload, String field) {
        String value = nullableString(payload, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Task payload " + field + " must not be blank");
        }
        return value;
    }

    private String nullableString(JsonNode payload, String field) {
        JsonNode value = required(payload, field);
        if (value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw new IllegalArgumentException("Task payload " + field + " must be a string");
        }
        return value.stringValue();
    }

    private Long requiredId(JsonNode payload, String field) {
        JsonNode value = required(payload, field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0) {
            throw new IllegalArgumentException("Task payload " + field + " must be a positive ID");
        }
        return value.longValue();
    }
}
