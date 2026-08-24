package com.fuma.hiselectors.taskrun.service;

import com.fuma.hiselectors.taskrun.model.TaskRunStatus;
import java.util.Objects;
import java.util.UUID;

public record TaskTerminalContext(UUID runId, TaskRunStatus status) {

    public TaskTerminalContext {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
