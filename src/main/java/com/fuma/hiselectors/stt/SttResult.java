package com.fuma.hiselectors.stt;

import io.swagger.v3.oas.annotations.media.Schema;

public record SttResult(

        @Schema(description = "영상 내용 요약. Gemini 가 전사와 같은 호출에서 생성. 없으면 빈 문자열")
        String summary,

        @Schema(description = "음성 전사(STT). 사람이 실제로 말한 내용. 없으면 빈 문자열")
        String stt,

        @Schema(description = "화면 텍스트(OCR). 음성과 무관한 자막·제목·그래픽. 없으면 빈 문자열")
        String ocr,

        @Schema(description = "LLM이 같은 호출에서 뽑은 정성 분석(스타일·톤·강점·위험·브랜드 등)")
        ContentInsight insight) {

    public static SttResult empty() {
        return new SttResult("", "", "", ContentInsight.empty());
    }
}
