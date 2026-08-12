package com.fuma.hiselectors.category.dto;

import com.fuma.hiselectors.category.model.DiscoveryKeyword;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 키워드 등록 결과.
 *
 * <p>{@code warnings} 는 막을 정도는 아니지만 관리자가 알아야 하는 사항이다.
 * 예) 같은 키워드가 다른 카테고리에도 등록되어 있음.
 */
@Schema(description = "키워드 등록 결과")
public record KeywordCreateResponse(

        @Schema(description = "등록된 키워드") KeywordResponse keyword,

        @Schema(description = "경고 (등록은 되었으나 확인이 필요한 사항)") List<String> warnings
) {

    public static KeywordCreateResponse of(DiscoveryKeyword keyword, List<String> warnings) {
        return new KeywordCreateResponse(KeywordResponse.from(keyword), warnings);
    }
}
