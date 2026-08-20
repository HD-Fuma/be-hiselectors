package com.fuma.hiselectors.selectors.dto;

import com.fuma.hiselectors.selectors.model.Selectors;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/** 셀렉터스 기본 정보 + 참여 기수 이력 + SNS 계정. */
@Schema(description = "셀렉터스 상세")
public record SelectorsDetailResponse(
        @Schema(description = "셀렉터스 ID") Long id,
        @Schema(description = "셀렉터스 코드") String selectorsCode,
        @Schema(description = "닉네임") String nickname,
        @Schema(description = "역할 코드", example = "ACTIVE") String roleId,
        @Schema(description = "역할명", example = "활성") String roleName,
        @Schema(description = "연결된 지원서 ID") Long applicationId,
        @Schema(description = "연결된 사용자 ID") Long userId,
        @Schema(description = "등록 시각") LocalDateTime createdAt,
        @Schema(description = "수정 시각") LocalDateTime updatedAt,
        @Schema(description = "참여 기수 이력 (최신순)")
        List<SelectorsGenerationResponse> generations,
        @Schema(description = "SNS 계정") SelectorsSnsAccountResponse snsAccount
) {

    public static SelectorsDetailResponse of(
            Selectors selectors,
            String roleName,
            List<SelectorsGenerationResponse> generations,
            SelectorsSnsAccountResponse snsAccount) {
        return new SelectorsDetailResponse(
                selectors.getId(),
                selectors.getSelectorsCode(),
                selectors.getSelectorsNickname(),
                selectors.getSelectorsRoleId(),
                roleName,
                selectors.getApplicationId(),
                selectors.getUserId(),
                selectors.getCreatedAt(),
                selectors.getUpdatedAt(),
                generations,
                snsAccount
        );
    }
}
