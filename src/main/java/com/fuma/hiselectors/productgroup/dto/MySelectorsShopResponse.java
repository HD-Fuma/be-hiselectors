package com.fuma.hiselectors.productgroup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "로그인한 셀렉터스의 샵 및 본인 프로필 정보")
public record MySelectorsShopResponse(
        @Schema(description = "셀렉터스 코드", example = "SEL-2601-001") String selectorsCode,
        @Schema(description = "셀렉터스 활동 닉네임") String nickname,
        @Schema(description = "대표 SNS 프로필 이미지 URL") String profileImageUrl,
        @Schema(description = "가장 최근 참여 기수명", example = "3기") String generationName,
        @Schema(description = "사용자 실명. 로그인한 본인에게만 제공") String userName,
        @Schema(description = "대표 SNS 계정 ID") String snsId,
        @Schema(description = "내 상품 그룹") List<ProductGroupResponse> groups
) {
}
