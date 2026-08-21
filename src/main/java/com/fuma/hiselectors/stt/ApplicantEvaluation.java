package com.fuma.hiselectors.stt;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 지원자 콘텐츠 N개를 합쳐 Gemini가 1회 산출한 정성 평가. */
public record ApplicantEvaluation(

        @Schema(description = "교정된 대표 카테고리(공식 코드)")
        String category,

        @Schema(description = "지원자 대표 키워드")
        List<String> keywords,

        @Schema(description = "지원자 콘텐츠 한줄 요약")
        String summary,

        @Schema(description = "톤·스타일")
        String tone) {
}
