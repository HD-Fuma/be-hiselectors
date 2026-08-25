package com.fuma.hiselectors.stt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class GeminiPropertiesTest {

    @Test
    void 리포트_파이프라인은_비용별_기본_모델을_사용한다() {
        GeminiProperties properties = new GeminiProperties(
                "key", null, null, null, null, null, null, null);

        assertEquals("gemini-3.5-flash-lite", properties.youtubeModelOrDefault());
        assertEquals("gemini-3.5-flash-lite", properties.reportModelOrDefault());
    }

    @Test
    void 같은_키의_모델을_모두_시도한_뒤_다음_키로_넘어간다() {
        GeminiProperties properties = new GeminiProperties(
                "key-1", "key-2,key-3", "fallback-a,fallback-b",
                "main", "youtube", "report", null, null);

        assertEquals(List.of(
                        new GeminiProperties.Attempt("primary", "key-1"),
                        new GeminiProperties.Attempt("fallback-a", "key-1"),
                        new GeminiProperties.Attempt("fallback-b", "key-1"),
                        new GeminiProperties.Attempt("main", "key-1"),
                        new GeminiProperties.Attempt("youtube", "key-1"),
                        new GeminiProperties.Attempt("report", "key-1"),
                        new GeminiProperties.Attempt("primary", "key-2")),
                properties.attempts("primary").subList(0, 7));
    }
}
