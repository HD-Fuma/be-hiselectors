package com.fuma.hiselectors.creator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * FAST 모드 카테고리 데모 발굴 결과.
 *
 * <p>{@code discoveredCreatorIds} 를 함께 내려주는 이유: 화면이 이번에 되살아난 계정만
 * "방금 발굴됨"으로 강조하기 위해서다. {@code discovered_at} 은 최초 발굴 시각이라
 * 갱신하지 않으므로, 강조 대상은 서버가 아니라 이 응답으로 전달한다.
 *
 * <p>두 값이 다른 이유: 이미 노출 중이던 계정은 발굴 결과에 함께 보이지만
 * 새로 나온 것이 아니므로 강조하지 않는다.
 */
@Schema(description = "FAST 모드 카테고리 데모 발굴 결과")
public record CreatorPoolCategoryDemoResponse(

        @Schema(description = "발굴 결과로 목록에 노출되는 계정 수") int visibleCount,
        @Schema(description = "이번에 새로 나와 강조할 크리에이터 풀 ID")
        List<Long> discoveredCreatorIds
) {
}
