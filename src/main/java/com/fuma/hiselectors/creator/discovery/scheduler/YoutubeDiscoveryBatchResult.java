package com.fuma.hiselectors.creator.discovery.scheduler;

/** 일일 YouTube 발굴 작업 전체의 실행 결과. */
public record YoutubeDiscoveryBatchResult(
        int runnableKeywords,
        int attemptedKeywords,
        int succeededKeywords,
        int failedKeywords,
        int reservedQuota,
        int consumedQuota,
        int discovered,
        int created,
        int updated
) {

    public static YoutubeDiscoveryBatchResult empty(int runnableKeywords) {
        return new YoutubeDiscoveryBatchResult(
                runnableKeywords, 0, 0, 0,
                0, 0, 0, 0, 0);
    }
}
