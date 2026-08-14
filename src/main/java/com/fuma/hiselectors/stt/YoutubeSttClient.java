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

    private static final String SPEECH_MARKER = "===음성===";
    private static final String CAPTION_MARKER = "===자막===";

    private static final String PROMPT = """
            이 유튜브 영상을 분석해 아래 형식 그대로만 출력하세요. 설명은 붙이지 마세요.
            ===음성===
            사람이 실제로 말한 내용을 한국어로 전사하세요. 그 말이 화면에 자막으로 떠 \
            있더라도 여기(음성)에만 넣으세요. 말이 없으면 비워 두세요.
            ===자막===
            음성과 무관하게 화면에 표시된 텍스트만 적으세요(영상 제목, 뉴스 자막바, \
            상표·라벨, 그래픽 문구 등). 음성을 그대로 받아쓴 자막은 넣지 마세요. \
            없으면 비워 두세요.""";

    private final GeminiProperties properties;
    private final RestClient restClient;

    public YoutubeSttClient(GeminiProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.create();
    }

    /** @return 음성·자막을 구분한 결과. 둘 다 없으면 빈 값. 저장하지 않는다. */
    public SttResult transcribe(String videoId) {
        if (!properties.hasApiKey()) {
            throw new BusinessException(ErrorCode.GEMINI_API_KEY_MISSING);
        }

        String url = "https://www.youtube.com/watch?v=" + videoId;
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(
                        Map.of("fileData", Map.of("fileUri", url)),
                        Map.of("text", PROMPT)))),
                "generationConfig", Map.of("mediaResolution", "MEDIA_RESOLUTION_LOW"));

        return parse(rawText(call(body)));
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

    private String rawText(GeminiResponse r) {
        if (r == null || r.candidates() == null || r.candidates().isEmpty()) {
            return "";
        }
        GeminiResponse.Content content = r.candidates().get(0).content();
        if (content == null || content.parts() == null || content.parts().isEmpty()) {
            return "";
        }
        String text = content.parts().get(0).text();
        return text == null ? "" : text;
    }

    private SttResult parse(String text) {
        int s = text.indexOf(SPEECH_MARKER);
        int c = text.indexOf(CAPTION_MARKER);
        if (s < 0 || c < 0 || c < s) {
            return new SttResult(text.trim(), "");
        }
        String speech = text.substring(s + SPEECH_MARKER.length(), c).trim();
        String caption = text.substring(c + CAPTION_MARKER.length()).trim();
        return new SttResult(speech, caption);
    }

    record GeminiResponse(List<Candidate> candidates) {
        record Candidate(Content content) { }

        record Content(List<Part> parts) { }

        record Part(String text) { }
    }
}
