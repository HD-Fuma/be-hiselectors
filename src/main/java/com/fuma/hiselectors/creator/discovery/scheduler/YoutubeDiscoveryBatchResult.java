package com.fuma.hiselectors.creator.discovery.scheduler;

/** 관리자가 시작한 YouTube 일괄 발굴의 실행 결과. */
public record YoutubeDiscoveryBatchResult(
        int runnableKeywords,
        int targetKeywords,
        int attemptedKeywords,
        int succeededKeywords,
        int failedKeywords,
        int reservedQuota,
        int consumedQuota,
        int discovered,
        int created,
        int updated,
        int uniqueCollectedCreators
) {
}
