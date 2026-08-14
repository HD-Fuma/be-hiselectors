package com.fuma.hiselectors.creator.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 영향력 순위를 계산할 때 사용하는 크리에이터 후보 지표. */
public record InfluenceCandidate(
        Long creatorId,
        String snsCode,
        String accountId,
        String creatorName,
        Long followerCount,
        BigDecimal engagementRate,
        LocalDateTime lastContentAt,
        String categoryCode,
        LocalDateTime discoveredAt,
        LocalDateTime updatedAt
) {

    /** 기존 호출부에서 갱신 시각이 없으면 최초 등록 시각과 같은 것으로 본다. */
    public InfluenceCandidate(
            Long creatorId,
            String snsCode,
            String accountId,
            String creatorName,
            Long followerCount,
            BigDecimal engagementRate,
            LocalDateTime lastContentAt,
            String categoryCode,
            LocalDateTime discoveredAt
    ) {
        this(creatorId, snsCode, accountId, creatorName, followerCount,
                engagementRate, lastContentAt, categoryCode,
                discoveredAt, discoveredAt);
    }
}
