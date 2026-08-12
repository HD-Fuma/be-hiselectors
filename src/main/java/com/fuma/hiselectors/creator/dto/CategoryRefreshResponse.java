package com.fuma.hiselectors.creator.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 대표 카테고리 재산출 결과.
 *
 * <p>발굴 출처가 없으면 재산출할 근거가 없으므로 기존 카테고리를 그대로 돌려준다.
 * 호출부는 {@code changed} 로 실제 갱신 여부를 구분한다.
 */
@Schema(description = "대표 카테고리 재산출 결과")
public record CategoryRefreshResponse(

        @Schema(description = "재산출 후의 대표 카테고리 코드", example = "BEAUTY")
        String categoryCode,

        @Schema(description = "발굴 출처를 집계해 실제로 갱신했는지 여부")
        boolean changed
) {

    public static CategoryRefreshResponse refreshed(String categoryCode) {
        return new CategoryRefreshResponse(categoryCode, true);
    }

    /** 발굴 출처가 없어 기존 값을 유지한 경우. */
    public static CategoryRefreshResponse unchanged(String categoryCode) {
        return new CategoryRefreshResponse(categoryCode, false);
    }
}
