package com.fuma.hiselectors.creator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 발굴된 크리에이터 목록의 한 줄. 계정 지표와 발굴 판정 근거를 함께 보여준다.
 *
 * <p>판정 근거({@code brandScore}, {@code igConfidence})를 그대로 내려주는 이유:
 * 수집 시점에 거르지 않고 저장해 둔 것을 화면에서 조건을 바꿔가며 걸러 보기 위해서다.
 */
@Schema(description = "발굴된 크리에이터 요약")
public record CreatorSummary(

        @Schema(description = "크리에이터 풀 ID") Long id,
        @Schema(description = "SNS 코드", example = "YOUTUBE") String snsCode,
        @Schema(description = "플랫폼 계정 ID", example = "UCxxxxxxxx") String accountId,
        @Schema(description = "크리에이터명") String creatorName,
        @Schema(description = "공개 프로필 이미지 URL") String profileImageUrl,
        @Schema(description = "팔로워/구독자 수") Long followerCount,
        @Schema(description = "ER 지수") BigDecimal engagementRate,
        @Schema(description = "최근 활동일") LocalDateTime lastContentAt,
        @Schema(description = "대표 카테고리 코드", example = "BEAUTY") String category,
        @Schema(description = "최근 90일 공개 콘텐츠 수") Integer recent90DayContentCount,

        @Schema(description = "브랜드 신호 점수. 2 이상이면 브랜드 계정으로 본다") Integer brandScore,
        @Schema(description = "채널 설명에서 추출한 인스타 핸들") String igHandle,
        @Schema(description = "핸들 추출 신뢰도. URL 0.95 / 라벨 0.75 / 멘션 0.35")
        BigDecimal igConfidence
) {
}
