package com.fuma.hiselectors.stt;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.service.InspectionPromptProvider;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class YoutubeSttClient {

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private static final String SUMMARY_MARKER = "===요약===";
    private static final String STT_MARKER = "===음성===";
    private static final String OCR_MARKER = "===자막===";
    private static final String ANALYSIS_MARKER = "===분석===";

    private final GeminiProperties properties;
    private final InspectionPromptProvider promptProvider;
    // 작은 분석 JSON 파싱용. 상태 없는 파서라 빈 주입 없이 직접 만든다.
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient;

    public YoutubeSttClient(GeminiProperties properties,
                            InspectionPromptProvider promptProvider) {
        this.properties = properties;
        this.promptProvider = promptProvider;
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
                        Map.of("text", promptProvider.youtubeSttPrompt())))),
                "generationConfig", Map.of(
                        "mediaResolution", properties.mediaResolutionApiValue(),
                        "thinkingConfig", Map.of("thinkingBudget", 0),
                        "maxOutputTokens", properties.maxOutputTokensOrDefault()));

        return parse(rawText(call(body)));
    }

    private GeminiResponse call(Map<String, Object> body) {
        String uri = ENDPOINT.formatted(properties.youtubeModelOrDefault());
        try {
            return restClient.post()
                    .uri(uri)
                    .header("x-goog-api-key", properties.apiKey())  // 키를 URL 대신 헤더로(로그 유출 방지)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(GeminiResponse.class);
        } catch (RestClientException e) {
            log.warn("Gemini STT 호출 실패. model={}", properties.youtubeModelOrDefault(), e);
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
        if (finish != null && !"STOP".equals(finish)) {
            // MAX_TOKENS(출력 잘림), SAFETY, RECITATION 등 → 불완전/차단이므로 실패.
            // 잘린 전사를 성공으로 반환하지 않는다. 잘리면 gemini.max-output-tokens 를 올린다.
            log.warn("Gemini 정상 종료 아님. finishReason={}", finish);
            throw new BusinessException(ErrorCode.GEMINI_API_CALL_FAILED);
        }
        GeminiResponse.Content content = candidate.content();
        if (content == null || content.parts() == null || content.parts().isEmpty()) {
            log.warn("Gemini 응답에 콘텐츠 없음. finishReason={}", finish);
            throw new BusinessException(ErrorCode.GEMINI_API_CALL_FAILED);
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

    private static final String[] MARKERS =
            { SUMMARY_MARKER, STT_MARKER, OCR_MARKER, ANALYSIS_MARKER };

    private SttResult parse(String text) {
        boolean noMarkers = text.indexOf(SUMMARY_MARKER) < 0 && text.indexOf(STT_MARKER) < 0
                && text.indexOf(OCR_MARKER) < 0 && text.indexOf(ANALYSIS_MARKER) < 0;
        if (noMarkers) {
            // 형식 이탈 = 마커 없는 응답. 통째로 음성 자리에 넣는다(요약으로 오인 방지).
            return new SttResult("", text.trim(), "", ContentInsight.empty());
        }
        return new SttResult(
                section(text, SUMMARY_MARKER),
                section(text, STT_MARKER),
                section(text, OCR_MARKER),
                parseInsight(section(text, ANALYSIS_MARKER)));
    }

    /** ===분석=== 섹션의 JSON을 파싱. 실패해도 STT 결과는 살리도록 빈 값으로 폴백한다. */
    private ContentInsight parseInsight(String raw) {
        int open = raw.indexOf('{');
        int close = raw.lastIndexOf('}');
        if (open < 0 || close <= open) {
            return ContentInsight.empty();
        }
        try {
            return objectMapper.readValue(raw.substring(open, close + 1), ContentInsight.class);
        } catch (Exception e) {
            log.warn("Gemini 분석 JSON 파싱 실패. 빈 분석으로 대체한다.", e);
            return ContentInsight.empty();
        }
    }

    /** marker 뒤부터 다음 마커(어느 것이든) 직전까지. 마커 순서가 바뀌어도 안전. */
    private String section(String text, String marker) {
        int start = text.indexOf(marker);
        if (start < 0) {
            return "";
        }
        start += marker.length();
        int end = text.length();
        for (String other : MARKERS) {
            if (other.equals(marker)) {
                continue;
            }
            int i = text.indexOf(other);
            if (i >= start && i < end) {
                end = i;
            }
        }
        return text.substring(start, end).trim();
    }

    record GeminiResponse(List<Candidate> candidates, PromptFeedback promptFeedback) {
        record Candidate(Content content, String finishReason) { }

        record Content(List<Part> parts) { }

        record Part(String text) { }

        record PromptFeedback(String blockReason) { }
    }
}
