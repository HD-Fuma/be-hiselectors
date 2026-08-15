package com.fuma.hiselectors.stt;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class YoutubeSttClient {

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private static final String STT_MARKER = "===음성===";
    private static final String OCR_MARKER = "===자막===";

    private static final String PROMPT = """
            이 유튜브 영상을 분석해 아래 형식 그대로만 출력하세요. 설명은 붙이지 마세요.
            두 항목은 독립적으로 각각 추출하며, 내용이 겹쳐도 그대로 둡니다.
            ===음성===
            오디오에서 사람이 말한 내용을 한국어로 전부 전사하세요. 없으면 비워 두세요.
            ===자막===
            화면에 보이는 텍스트를 전부 적으세요(자막, 제목, 상표·라벨, 그래픽 문구 등). \
            음성과 겹치더라도 그대로 적으세요. 없으면 비워 두세요.""";

    private final GeminiProperties properties;
    private final RestClient restClient;

    public YoutubeSttClient(GeminiProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        // 영상 분석은 수십 초~분까지 걸릴 수 있어 응답 제한을 넉넉히 둔다.
        factory.setReadTimeout(Duration.ofMinutes(5));
        this.restClient = RestClient.builder().requestFactory(factory).build();
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
                "generationConfig", Map.of("mediaResolution", properties.mediaResolutionOrDefault()));

        return parse(rawText(call(body)));
    }

    private GeminiResponse call(Map<String, Object> body) {
        String uri = ENDPOINT.formatted(properties.modelOrDefault());
        try {
            return restClient.post()
                    .uri(uri)
                    .header("x-goog-api-key", properties.apiKey())  // 키를 URL 대신 헤더로(로그 유출 방지)
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
            // 후보 없음 = 안전 차단 또는 실패. 빈 성공으로 넘기지 않고 실패 처리.
            log.warn("Gemini 응답에 후보 없음. blockReason={}", blockReason(r));
            throw new BusinessException(ErrorCode.GEMINI_API_CALL_FAILED);
        }
        GeminiResponse.Candidate candidate = r.candidates().get(0);
        String finish = candidate.finishReason();
        if (finish != null && !"STOP".equals(finish) && !"MAX_TOKENS".equals(finish)) {
            // SAFETY, RECITATION 등 비정상 종료.
            log.warn("Gemini 비정상 종료. finishReason={}", finish);
            throw new BusinessException(ErrorCode.GEMINI_API_CALL_FAILED);
        }
        GeminiResponse.Content content = candidate.content();
        if (content == null || content.parts() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (GeminiResponse.Part part : content.parts()) {
            if (part.text() != null) {
                sb.append(part.text());
            }
        }
        return sb.toString();
    }

    private String blockReason(GeminiResponse r) {
        return r != null && r.promptFeedback() != null ? r.promptFeedback().blockReason() : null;
    }

    private SttResult parse(String text) {
        String stt = section(text, STT_MARKER, OCR_MARKER);
        String ocr = section(text, OCR_MARKER, STT_MARKER);
        if (stt.isEmpty() && ocr.isEmpty()
                && text.indexOf(STT_MARKER) < 0 && text.indexOf(OCR_MARKER) < 0) {
            return new SttResult(text.trim(), "");
        }
        return new SttResult(stt, ocr);
    }

    private String section(String text, String marker, String otherMarker) {
        int start = text.indexOf(marker);
        if (start < 0) {
            return "";
        }
        start += marker.length();
        int other = text.indexOf(otherMarker);
        int end = other > start ? other : text.length();
        return text.substring(start, end).trim();
    }

    record GeminiResponse(List<Candidate> candidates, PromptFeedback promptFeedback) {
        record Candidate(Content content, String finishReason) { }

        record Content(List<Part> parts) { }

        record Part(String text) { }

        record PromptFeedback(String blockReason) { }
    }
}
