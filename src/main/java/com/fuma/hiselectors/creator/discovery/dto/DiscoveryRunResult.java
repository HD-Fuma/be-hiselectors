package com.fuma.hiselectors.creator.discovery.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 발굴 1회 실행 결과.
 *
 * <p>{@code consumedQuota} 를 함께 돌려주는 이유: 일일 한도가 10,000 units 이고
 * 키워드 1개가 약 102 units 를 쓴다. 얼마나 남았는지 감을 잡으려면 매번 보여야 한다.
 */
@Schema(description = "발굴 실행 결과")
public record DiscoveryRunResult(

        @Schema(description = "실행한 검색 키워드", example = "겟레디윗미") String keyword,
        @Schema(description = "키워드가 속한 카테고리 코드", example = "BEAUTY") String categoryCode,
        @Schema(description = "발굴된 채널 수") int discovered,
        @Schema(description = "신규 등록된 계정 수") int created,
        @Schema(description = "기존 계정 갱신 수") int updated,
        @Schema(description = "이번 실행에 사용한 API 쿼터 (일일 한도 10,000)") int consumedQuota
) {
}
