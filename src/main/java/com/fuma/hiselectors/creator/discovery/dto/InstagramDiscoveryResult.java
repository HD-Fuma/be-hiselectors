package com.fuma.hiselectors.creator.discovery.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** YouTube 계정에서 연결된 Instagram 계정을 발굴하고 저장한 결과. */
public record InstagramDiscoveryResult(
        Long sourceCreatorId,
        Long instagramCreatorId,
        String username,
        boolean created,
        Long followerCount,
        Long mediaCount,
        BigDecimal engagementRate,
        LocalDateTime lastContentAt
) {
}
