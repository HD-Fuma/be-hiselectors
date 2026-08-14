package com.fuma.hiselectors.stt;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class YoutubeSttClient {

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private static final String PROMPT =
            "이 유튜브 영상의 말과 화면 자막 내용을 전부 한국어 텍스트로 옮겨 주세요. "
            + "설명이나 해설 없이 옮긴 내용만 출력하세요. 말·자막이 전혀 없으면 빈 문자열을 출력하세요.";

    private final GeminiProperties properties;
    private final RestClient restClient;

    public YoutubeSttClient(GeminiProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.create();
    }

    /** @return 영상에서 옮긴 텍스트. 말·자막이 없으면 빈 문자열. 저장하지 않는다. */
    public String transcribe(String videoId) {
        if (!properties.hasApiKey()) {
            throw new BusinessException(ErrorCode.GEMINI_API_KEY_MISSING);
        }

        String url = "https://www.youtube.com/watch?v=" + videoId;
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(
                        Map.of("fileData", Map.of("fileUri", url)),
                        Map.of("text", PROMPT)))),
                "generationConfig", Map.of("mediaResolution", "MEDIA_RESOLUTION_LOW"));

        return extractText(call(body));
    }

    private GeminiResponse call(Map<String, Object> body) {
        String uri = ENDPOINT.formatted(properties.modelOrDefault(), properties.apiKey());
        try {
            return restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(GeminiResponse.class);
        } catch (RestClientException e) {
            log.warn("Gemini STT 호출 실패. model={}", properties.modelOrDefault(), e);
            throw new BusinessException(ErrorCode.GEMINI_API_CALL_FAILED);
        }
    }

    private String extractText(GeminiResponse r) {
        if (r == null || r.candidates() == null || r.candidates().isEmpty()) {
            return "";
        }
        GeminiResponse.Content content = r.candidates().get(0).content();
        if (content == null || content.parts() == null || content.parts().isEmpty()) {
            return "";
        }
        String text = content.parts().get(0).text();
        return text == null ? "" : text.trim();
    }

    record GeminiResponse(List<Candidate> candidates) {
        record Candidate(Content content) { }

        record Content(List<Part> parts) { }

        record Part(String text) { }
    }
}
