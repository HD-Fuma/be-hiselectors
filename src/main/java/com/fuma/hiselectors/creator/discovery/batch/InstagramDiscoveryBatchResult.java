package com.fuma.hiselectors.creator.discovery.batch;

/** 관리자가 시작한 Instagram 일괄 발굴의 실행 결과. */
public record InstagramDiscoveryBatchResult(
        int targetCreators,
        int attemptedCreators,
        int succeededCreators,
        int failedCreators,
        int createdCreators,
        int updatedCreators,
        int uniqueCollectedCreators
) {
}
