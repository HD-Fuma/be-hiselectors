package com.fuma.hiselectors.taskrun.service;

@FunctionalInterface
public interface TrackedTask {

    void execute(TaskExecutionContext context) throws Exception;

    default void afterTerminal(TaskTerminalContext context) {
    }
}
