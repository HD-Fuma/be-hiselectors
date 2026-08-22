package com.fuma.hiselectors.creator.dto;

import com.fuma.hiselectors.creator.model.CreatorDiscoveryInfo;
import com.fuma.hiselectors.creator.model.CreatorPool;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "발굴 크리에이터 기본 상세 정보")
public record CreatorDetailResponse(

        @Schema(description = "크리에이터 풀 ID") Long id,
        @Schema(description = "SNS 코드", example = "YOUTUBE") String snsCode,
        @Schema(description = "플랫폼 계정 ID", example = "UCxxxxxxxx") String accountId,
        @Schema(description = "크리에이터명") String creatorName,
        @Schema(description = "팔로워/구독자 수") Long followerCount,
        @Schema(description = "ER 지수") BigDecimal engagementRate,
        @Schema(description = "최근 활동일") LocalDateTime lastContentAt,
        @Schema(description = "대표 카테고리 코드", example = "BEAUTY") String category,
        @Schema(description = "카테고리별 발굴 비중") List<CategoryShare> categoryShares,

        @Schema(description = "브랜드 신호 점수. 2 이상이면 브랜드 계정으로 본다")
        Integer brandScore,
        @Schema(description = "브랜드 판정 근거") String brandHits,
        @Schema(description = "채널 설명에서 추출한 Instagram 사용자명") String igHandle,
        @Schema(description = "Instagram 사용자명 추출 신뢰도") BigDecimal igConfidence,

        @Schema(description = "크리에이터 풀 등록일") LocalDateTime registeredAt,
        @Schema(description = "발굴 정보 최초 수집일") LocalDateTime firstDiscoveredAt,
        @Schema(description = "크리에이터 풀 최근 갱신일") LocalDateTime updatedAt
) {

    public static CreatorDetailResponse of(
            CreatorPool creator,
            CreatorDiscoveryInfo discoveryInfo,
            List<CategoryShare> categoryShares) {
        return new CreatorDetailResponse(
                creator.getId(),
                creator.getSnsCode(),
                creator.getAccountId(),
                creator.getCreatorName(),
                creator.getFollowerCount(),
                creator.getEngagementRate(),
                creator.getLastContentAt(),
                creator.getCategory(),
                List.copyOf(categoryShares),
                discoveryInfo == null ? null : discoveryInfo.getBrandScore(),
                discoveryInfo == null ? null : discoveryInfo.getBrandHits(),
                discoveryInfo == null ? null : discoveryInfo.getIgHandle(),
                discoveryInfo == null ? null : discoveryInfo.getIgConfidence(),
                creator.getCreatedAt(),
                discoveryInfo == null ? null : discoveryInfo.getDiscoveredAt(),
                creator.getUpdatedAt()
        );
    }
}
