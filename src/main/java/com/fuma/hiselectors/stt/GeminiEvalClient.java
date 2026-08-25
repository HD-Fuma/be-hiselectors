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
 * Instagram 취합 단계 LLM. 지원자 콘텐츠들의 전사·자막(application_content_analysis)을 합쳐
 * Gemini에 1회 던져 정성 insight(스타일·톤·강점·유의·위험·브랜드)를 뽑는다.
 * 콘텐츠별로 LLM을 안 태우고(=취득 자유로운 인스타의 이점) 취합 시 한 번만 태워 비용을 아낀다.
 * category·keywords 는 로컬 분석 결과가 비었을 때만 fallback으로 사용한다.
 */
@Slf4j
@Component
public class GeminiEvalClient {

    private static final int MAX_OUTPUT_TOKENS = 1024;

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private static final String PROMPT = """
            아래는 한 지원자의 여러 콘텐츠에서 추출한 음성 전사와 화면 텍스트를 합친 것이다
            (OCR 노이즈가 섞일 수 있으니 감안해라). 이 지원자를 종합 평가해 아래 JSON 하나만 출력해라.
            콘텐츠를 개별적으로 나열하지 말고 2개 이상에서 반복되는 주제·형식·톤·강점을 공통점으로
            우선 평가해라. 한 콘텐츠에만 나온 소재는 지원자의 전반적 특징으로 단정하지 마라.
            설명·마크다운·코드펜스 없이 JSON 객체만 낸다.
            {
              "summary": "여러 콘텐츠에서 반복되는 공통 주제·형식·특징을 중심으로 2~3문장으로 서술",
              "category": "BEAUTY|FASHION|FOOD|LIVING_LIFE|KIDS_FAMILY|CULTURE_SERVICE|SPORTS_LEISURE|TRAVEL|PET_LIFE 중 가장 가까운 하나",
              "keywords": ["콘텐츠 핵심 키워드. 최대 5개, 명사형 단어 또는 짧은 명사구"],
              "contentStyle": "리뷰언박싱|튜토리얼|브이로그|챌린지|소통Q&A|하울|비교추천|정보설명|인터뷰|숏폼밈|라이브 중 하나",
              "tone": "유쾌코믹|차분잔잔|진지전문|친근수다|감성무드|자극과장|시니컬솔직 중 하나",
              "strengths": ["크리에이터·콘텐츠의 강점. 문장이 아니라 명사구 단답형으로, 15자 내외. 예: 반응이 상세함, 리뷰가 좋은 편"],
              "cautions": ["브랜드 협업 시 유의점. 문장 금지, 명사구 단답형 15자 내외"],
              "risks": ["정치종교논란|허위과장광고|미검증건강주장|선정성|저작권|사행성 중 해당되는 것만, 없으면 빈 배열"],
              "hateConfirmed": false,
              "collabBrands": ["협찬·협업으로 보이는 브랜드명만. 설명·문장 금지, 브랜드 이름만. 플랫폼·크리에이터 본인은 제외. 없으면 빈 배열"]
            }
            [콘텐츠 모음]
            %s""";

    private final GeminiProperties properties;
    private final GeminiRequestExecutor requestExecutor;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient;

    public GeminiEvalClient(GeminiProperties properties, GeminiRequestExecutor requestExecutor) {
        this.properties = properties;
        this.requestExecutor = requestExecutor;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofMinutes(2));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** 합본 transcript → 지원자 종합 insight(서술 요약 포함). */
    public ApplicantInsight insight(String mergedTranscript) {
        if (!properties.hasApiKey()) {
            throw new BusinessException(ErrorCode.GEMINI_API_KEY_MISSING);
        }

        String prompt = PROMPT.formatted(mergedTranscript);
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "thinkingConfig", Map.of("thinkingLevel", "minimal"),
                        "maxOutputTokens", MAX_OUTPUT_TOKENS));

        return parse(rawText(call(body)));
    }

    private GeminiResponse call(Map<String, Object> body) {
        try {
            return requestExecutor.execute(properties.reportModelOrDefault(), attempt ->
                    restClient.post()
                            .uri(ENDPOINT.formatted(attempt.model()))
                            .header("x-goog-api-key", attempt.apiKey())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(GeminiResponse.class));
        } catch (RestClientException e) {
            log.warn("Gemini 취합 후보를 모두 소진했습니다.", e);
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
            log.warn("Gemini 취합 정상 종료 아님. finishReason={}", finish);
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

    private ApplicantInsight parse(String json) {
        try {
            return objectMapper.readValue(stripCodeFence(json), ApplicantInsight.class);
        } catch (RuntimeException e) {
            log.warn("Gemini 취합 JSON 파싱 실패. body={}", json, e);
            throw new BusinessException(ErrorCode.GEMINI_EVAL_PARSE_FAILED);
        }
    }

    /** LLM이 ```json …``` 코드펜스로 감싸 보내는 환각 대비. 펜스만 벗겨 순수 JSON을 남긴다. */
    private String stripCodeFence(String s) {
        return s.replace("```json", "").replace("```", "").trim();
    }

    record GeminiResponse(List<Candidate> candidates) {
        record Candidate(Content content, String finishReason) { }

        record Content(List<Part> parts) { }

        record Part(String text) { }
    }
}
