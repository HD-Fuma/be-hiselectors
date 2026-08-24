package com.fuma.hiselectors.taskrun.service;

public interface TaskProgressReporter {

    void start(String stepCode, Integer totalCount);

    void changeStep(String stepCode);

    void describe(String message);

    void advance(int succeededDelta, int failedDelta, int skippedDelta);

    void heartbeat();
}
