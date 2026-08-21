package com.fuma.hiselectors.stt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 지원자 콘텐츠 종합 취합 LLM(Gemini) 결과. 콘텐츠별 ContentInsight(YouTube)와 달리
 * 여러 콘텐츠를 합쳐 "이 지원자의 콘텐츠는 이렇다"는 서술 요약(summary)까지 포함한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApplicantInsight(

        /** "이 지원자 콘텐츠는 이런 식이다" 서술 요약(2~3문장). */
        String summary,

        String contentStyle,
        String tone,
        List<String> strengths,
        List<String> cautions,
        List<String> risks,
        boolean hateConfirmed,
        List<String> collabBrands) {
}
