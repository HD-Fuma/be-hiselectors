package com.fuma.hiselectors.taskrun.service;

import com.fuma.hiselectors.taskrun.model.TaskType;
import com.fuma.hiselectors.taskrun.model.TriggerType;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record TaskStartCommand(
        TaskType taskType,
        TriggerType triggerType,
        Long startedByAdminId,
        UUID idempotencyKey,
        JsonNode businessPayload) {

    public TaskStartCommand {
        Objects.requireNonNull(taskType, "taskType must not be null");
        Objects.requireNonNull(triggerType, "triggerType must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(businessPayload, "businessPayload must not be null");
    }
}
