package com.fuma.hiselectors.creator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * FAST 모드 카테고리 데모 발굴 결과.
 *
 * <p>{@code restoredCreatorIds} 를 함께 내려주는 이유: 화면이 이번에 되살아난 계정만
 * "방금 발굴됨"으로 강조하기 위해서다. {@code discovered_at} 은 최초 발굴 시각이라
 * 갱신하지 않으므로, 강조 대상은 서버가 아니라 이 응답으로 전달한다.
 */
@Schema(description = "FAST 모드 카테고리 데모 발굴 결과")
public record CreatorPoolCategoryDemoResponse(

        @Schema(description = "발굴 결과로 목록에 노출되는 계정 수") int restoredCount,
        @Schema(description = "강조 대상 크리에이터 풀 ID") List<Long> restoredCreatorIds
) {
}
