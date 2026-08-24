package com.fuma.hiselectors.taskrun.service;

import java.util.Objects;

public record TaskExecutionContext(TaskLease lease, TaskProgressReporter progress) {

    public TaskExecutionContext {
        Objects.requireNonNull(lease, "lease must not be null");
        Objects.requireNonNull(progress, "progress must not be null");
    }
}
