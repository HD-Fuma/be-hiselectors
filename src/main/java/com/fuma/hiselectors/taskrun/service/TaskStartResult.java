package com.fuma.hiselectors.taskrun.service;

import com.fuma.hiselectors.taskrun.model.TaskRun;

public sealed interface TaskStartResult {

    record Created(TaskRun run) implements TaskStartResult {
    }

    record Replayed(TaskRun run) implements TaskStartResult {
    }

    record ActiveConflict(TaskRun activeRun) implements TaskStartResult {
    }
}
