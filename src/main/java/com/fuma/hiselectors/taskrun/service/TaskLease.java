package com.fuma.hiselectors.taskrun.service;

import java.util.Objects;
import java.util.UUID;

public record TaskLease(UUID runId, UUID token) {

    public TaskLease {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(token, "token must not be null");
    }
}
