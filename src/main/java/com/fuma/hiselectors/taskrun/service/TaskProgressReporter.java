package com.fuma.hiselectors.taskrun.service;

public interface TaskProgressReporter {

    void start(String stepCode, Integer totalCount);

    void changeStep(String stepCode);

    void describe(String message);

    void recordFailure(String type, String message);

    void reportStep(String stepKey, Long totalCount, long processedCount);

    void advance(int succeededDelta, int failedDelta, int skippedDelta);

    void heartbeat();
}
