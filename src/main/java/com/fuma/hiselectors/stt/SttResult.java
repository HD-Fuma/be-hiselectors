package com.fuma.hiselectors.stt;

import io.swagger.v3.oas.annotations.media.Schema;

public record SttResult(

        @Schema(description = "사람이 실제로 말한 내용(음성 전사). 없으면 빈 문자열")
        String speech,

        @Schema(description = "음성과 무관하게 화면에 표시된 텍스트(제목·자막바·그래픽 등). 없으면 빈 문자열")
        String caption) {

    public static SttResult empty() {
        return new SttResult("", "");
    }
}
