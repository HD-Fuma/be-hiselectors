package com.fuma.hiselectors.stt;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/** 파이썬 워커 {@code POST /reel} 응답. 릴스 URL → 취득 → STT/OCR → 정성 분석 결과. */
public record InstagramAnalysisResult(

        @Schema(description = "분석 소스. video=영상(음성+화면), thumbnail=썸네일 폴백(화면만)")
        String source,

        @Schema(description = "음성 전사(STT). 썸네일 폴백이면 빈 값")
        String stt,

        @Schema(description = "화면 텍스트(OCR)")
        String ocr,

        @Schema(description = "정성 분석(키워드·카테고리·욕설혐오)")
        Analysis analysis) {

    public record Analysis(List<String> keywords, Category category, Hate hate) { }

    public record Category(String label, Double score, Boolean uncertain) { }

    public record Hate(List<String> labels, Map<String, Double> scores, Boolean suspected) { }
}
