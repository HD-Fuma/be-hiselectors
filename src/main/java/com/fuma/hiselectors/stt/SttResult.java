package com.fuma.hiselectors.stt;

import io.swagger.v3.oas.annotations.media.Schema;

public record SttResult(

        @Schema(description = "영상 내용 요약. 현재 비용 최적화 경로에서는 빈 문자열")
        String summary,

        @Schema(description = "핵심 음성 전사(STT). 최대 1,000자, 없으면 빈 문자열")
        String stt,

        @Schema(description = "중복 제거 화면 텍스트(OCR). 최대 500자, 없으면 빈 문자열")
        String ocr,

        @Schema(description = "콘텐츠 정성 분석. 현재 비용 최적화 경로에서는 빈 값")
        ContentInsight insight) {

    public static SttResult empty() {
        return new SttResult("", "", "", ContentInsight.empty());
    }
}
