package com.fuma.hiselectors.category.dto;

import com.fuma.hiselectors.category.model.Category;
import com.fuma.hiselectors.category.model.DiscoveryKeyword;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Comparator;
import java.util.List;

@Schema(description = "발굴 카테고리 (하위 키워드 포함)")
public record CategoryResponse(

        @Schema(description = "카테고리 ID") Long id,
        @Schema(description = "카테고리 코드", example = "BEAUTY") String code,
        @Schema(description = "카테고리명", example = "뷰티") String name,
        @Schema(description = "화면 노출 순서") int displayOrder,
        @Schema(description = "발굴 대상 여부") boolean enabled,
        @Schema(description = "하위 발굴 키워드") List<KeywordResponse> keywords
) {

    /** 키워드는 우선순위 높은 순 → 등록 순으로 내려준다. */
    private static final Comparator<DiscoveryKeyword> KEYWORD_ORDER =
            Comparator.comparingInt(DiscoveryKeyword::getPriority).reversed()
                    .thenComparing(DiscoveryKeyword::getId);

    public static CategoryResponse from(Category category) {
        List<KeywordResponse> keywords = category.getKeywords().stream()
                .sorted(KEYWORD_ORDER)
                .map(KeywordResponse::from)
                .toList();

        return new CategoryResponse(
                category.getId(),
                category.getCode(),
                category.getName(),
                category.getDisplayOrder(),
                category.isEnabled(),
                keywords
        );
    }
}
