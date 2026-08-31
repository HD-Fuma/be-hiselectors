package com.fuma.hiselectors.taskrun.config;

import com.fuma.hiselectors.taskrun.model.TaskType;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class TaskTypePolicy {

    private final Map<TaskType, Settings> registry;

    public TaskTypePolicy(TaskRunProperties properties) {
        TaskRunProperties.Timeouts timeouts = properties.stale().timeouts();
        EnumMap<TaskType, Settings> policies = new EnumMap<>(TaskType.class);
        policies.put(TaskType.CREATOR_SYNC, settings(true, timeouts.creatorSync()));
        policies.put(TaskType.CONTENT_SYNC, settings(true, timeouts.contentSync()));
        policies.put(TaskType.APPLICATION_REPORT_GENERATION,
                settings(true, timeouts.applicationReportGeneration()));
        policies.put(TaskType.CONTENT_REPORT_GENERATION,
                settings(true, timeouts.contentReportGeneration()));
        policies.put(TaskType.SETTLEMENT_CALCULATION,
                settings(true, timeouts.settlementCalculation()));
        policies.put(TaskType.KAKAO_MESSAGE_SEND, settings(false, timeouts.kakaoMessageSend()));
        policies.put(TaskType.PROPOSAL_EMAIL_SEND, settings(false, timeouts.proposalEmailSend()));
        policies.put(TaskType.SELECTOR_PROPOSAL_EMAIL_SEND,
                settings(false, timeouts.selectorProposalEmailSend()));
        registry = Map.copyOf(policies);
    }

    public Settings forType(TaskType type) {
        return Objects.requireNonNull(registry.get(type), "unsupported task type: " + type);
    }

    private Settings settings(boolean singleton, Duration staleTimeout) {
        return new Settings(singleton, staleTimeout);
    }

    public record Settings(boolean singleton, Duration staleTimeout) {
    }
}
