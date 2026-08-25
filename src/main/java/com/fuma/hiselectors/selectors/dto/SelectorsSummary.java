package com.fuma.hiselectors.selectors.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 셀렉터스 목록 한 줄.
 *
 * <p>셀렉터스에 연결된 SNS 계정을 담는다.
 */
@Schema(description = "셀렉터스 목록 항목")
public record SelectorsSummary(
        @Schema(description = "셀렉터스 ID") Long id,
        @Schema(description = "셀렉터스 코드", example = "SEL0001") String selectorsCode,
        @Schema(description = "닉네임") String nickname,
        @Schema(description = "역할 코드", example = "ACTIVE") String roleId,
        @Schema(description = "역할명", example = "활성") String roleName,
        @Schema(description = "분석 카테고리 코드", example = "BEAUTY") String category,
        @Schema(description = "대표 SNS 플랫폼", example = "YOUTUBE") String snsCode,
        @Schema(description = "대표 SNS 계정 ID") String snsAccountId,
        @Schema(description = "대표 SNS 표시명") String snsDisplayName,
        @Schema(description = "대표 SNS 팔로워 수") Long followerCount,
        @Schema(description = "대표 SNS 프로필 이미지") String profileImageUrl,
        @Schema(description = "등록 시각") LocalDateTime createdAt
) {
}
