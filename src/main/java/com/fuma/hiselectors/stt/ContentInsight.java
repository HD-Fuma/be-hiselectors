package com.fuma.hiselectors.stt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ContentInsight(

        @Schema(description = "콘텐츠 스타일 (enum 중 하나)")
        String contentStyle,

        @Schema(description = "톤앤매너 (enum 중 하나)")
        String tone,

        @Schema(description = "강점")
        List<String> strengths,

        @Schema(description = "유의점")
        List<String> cautions,

        @Schema(description = "넓은 위험요소 (정치/광고과장/건강주장/선정성 등). 없으면 빈 배열")
        List<String> risks,

        @Schema(description = "욕설·혐오가 실제로 맞는지 LLM 확정값")
        boolean hateConfirmed,

        @Schema(description = "언급된 협업/협찬 추정 브랜드. 없으면 빈 배열")
        List<String> collabBrands) {

    public static ContentInsight empty() {
        return new ContentInsight("", "", List.of(), List.of(), List.of(), false, List.of());
    }
}
