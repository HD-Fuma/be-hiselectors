package com.fuma.hiselectors.content.dto;

/** 관리자가 실행한 활성 기수 콘텐츠 수집 결과. */
public record ContentCollectionBatchResponse(
        Long generationId,
        String generationName,
        int targetAccountCount,
        int succeededAccountCount,
        int failedAccountCount,
        int savedContentCount
) {
}
