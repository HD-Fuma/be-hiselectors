package com.fuma.hiselectors.productgroup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "공개 셀렉터스 샵")
public record SelectorsShopResponse(
        @Schema(description = "셀렉터스 코드", example = "SEL-2602-005") String selectorsCode,
        @Schema(description = "셀렉터스 닉네임") String nickname,
        @Schema(description = "대표 SNS 프로필 이미지 URL") String profileImageUrl,
        @Schema(description = "공개 상품 그룹") List<ProductGroupResponse> groups
) {
}
