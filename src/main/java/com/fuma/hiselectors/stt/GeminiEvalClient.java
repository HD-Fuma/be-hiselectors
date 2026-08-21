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
import tools.jackson.databind.ObjectMapper;

/**
 * 지원자 콘텐츠 transcript(N개 합본)를 Gemini에 1회 던져 정성 평가를 JSON으로 받는다.
 * 콘텐츠별 로컬 신호(keywords/category)는 참고용이고, 최종 판단·카테고리 교정은 여기(LLM)서 한다.
 */
@Slf4j
@Component
public class GeminiEvalClient {

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private static final String CATEGORIES =
            "BEAUTY, FASHION, FOOD, LIVING_LIFE, KIDS_FAMILY, CULTURE_SERVICE, "
                    + "SPORTS_LEISURE, TRAVEL, PET_LIFE";

    private static final String PROMPT = """
            너는 크리에이터 심사 보조자다. 아래는 한 지원자의 여러 콘텐츠에서 추출한 음성 전사와
            화면 텍스트를 합친 것이다(OCR 노이즈가 섞일 수 있으니 감안해라). 이 지원자를 종합 평가해
            아래 JSON 스키마로만 출력해라. 설명·마크다운 없이 JSON 객체 하나만 낸다.
            (광고성·정치·건강·과장광고 등 위험 판단은 하지 마라 — 다른 단계에서 처리한다.)
            {
              "category": "다음 중 하나: %s",
              "keywords": ["대표 키워드 5~10개"],
              "summary": "지원자를 한 문장으로 요약",
              "tone": "톤·스타일 한두 단어"
            }
            [콘텐츠 모음]
            %s""";

    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GeminiEvalClient(GeminiProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofMinutes(2));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public ApplicantEvaluation evaluate(String mergedTranscript) {
        if (!properties.hasApiKey()) {
            throw new BusinessException(ErrorCode.GEMINI_API_KEY_MISSING);
        }

        String prompt = PROMPT.formatted(CATEGORIES, mergedTranscript);
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "maxOutputTokens", properties.maxOutputTokensOrDefault()));

        return parse(rawText(call(body)));
    }

    private GeminiResponse call(Map<String, Object> body) {
        String uri = ENDPOINT.formatted(properties.modelOrDefault());
        try {
            return restClient.post()
                    .uri(uri)
                    .header("x-goog-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(GeminiResponse.class);
        } catch (RestClientException e) {
            log.warn("Gemini 평가 호출 실패. model={}", properties.modelOrDefault(), e);
            throw new BusinessException(ErrorCode.GEMINI_API_CALL_FAILED);
        }
    }

    private String rawText(GeminiResponse r) {
        if (r == null || r.candidates() == null || r.candidates().isEmpty()) {
            throw new BusinessException(ErrorCode.GEMINI_API_CALL_FAILED);
        }
        GeminiResponse.Candidate candidate = r.candidates().get(0);
        String finish = candidate.finishReason();
        if (finish != null && !"STOP".equals(finish)) {
            // MAX_TOKENS(잘림)·SAFETY 등 → JSON 불완전이므로 실패 처리.
            log.warn("Gemini 평가 정상 종료 아님. finishReason={}", finish);
            throw new BusinessException(ErrorCode.GEMINI_API_CALL_FAILED);
        }
        GeminiResponse.Content content = candidate.content();
        if (content == null || content.parts() == null || content.parts().isEmpty()) {
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

    private ApplicantEvaluation parse(String json) {
        try {
            return objectMapper.readValue(json, ApplicantEvaluation.class);
        } catch (RuntimeException e) {
            log.warn("Gemini 평가 JSON 파싱 실패. body={}", json, e);
            throw new BusinessException(ErrorCode.GEMINI_EVAL_PARSE_FAILED);
        }
    }

    record GeminiResponse(List<Candidate> candidates) {
        record Candidate(Content content, String finishReason) { }

        record Content(List<Part> parts) { }

        record Part(String text) { }
    }
}
