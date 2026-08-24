package com.fuma.hiselectors.inspection.ai;

import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.model.AiInspectionResponse;
import com.fuma.hiselectors.inspection.model.InspectionPolicy;
import com.fuma.hiselectors.inspection.model.IntegratedInspectionResult;
import com.fuma.hiselectors.stt.ContentInsight;
import com.fuma.hiselectors.stt.GeminiProperties;
import com.fuma.hiselectors.stt.SttResult;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** YouTube 원본 영상에서 추출과 정책 검수를 한 Gemini 호출로 수행한다. */
@Slf4j
@Component
public class YoutubeIntegratedInspectionClient {

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;
    private final GeminiAiInspectionClient inspectionMapper;
    private final RestClient restClient;

    public YoutubeIntegratedInspectionClient(
            GeminiProperties properties,
            ObjectMapper objectMapper,
            GeminiAiInspectionClient inspectionMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.inspectionMapper = inspectionMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofMinutes(5));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public IntegratedInspectionResult inspect(
            String videoId,
            Content content,
            ContentMedia video,
            List<ContentMedia> allMedia,
            InspectionPolicy policy) {
        if (!properties.hasApiKey()) {
            throw new BusinessException(ErrorCode.GEMINI_API_KEY_MISSING);
        }
        try {
            String input = objectMapper.writeValueAsString(Map.of(
                    "sns", content.getSnsCode(),
                    "contentUrl", content.getContentUrl(),
                    "videoContentMediaId", video.getId(),
                    "media", allMedia.stream().map(media -> Map.of(
                            "contentMediaId", media.getId(),
                            "mediaType", media.getMediaType(),
                            "body", media.bodyOrEmpty())).toList()));
            String prompt = policy.getExtractionPrompt()
                    + "\n\n"
                    + policy.getAiPrompt().formatted(input);
            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of("parts", List.of(
                            Map.of("fileData", Map.of(
                                    "fileUri", "https://www.youtube.com/watch?v=" + videoId)),
                            Map.of("text", prompt)))),
                    "generationConfig", Map.of(
                            "mediaResolution", properties.mediaResolutionApiValue(),
                            "responseMimeType", "application/json",
                            "responseJsonSchema", integratedResponseSchema(),
                            "maxOutputTokens", properties.maxOutputTokensOrDefault()));
            GeminiResponse response = restClient.post()
                    .uri(ENDPOINT.formatted(policy.getAiModelName()))
                    .header("x-goog-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(GeminiResponse.class);
            return map(extractText(response));
        } catch (RestClientException | JacksonException | IllegalArgumentException e) {
            log.warn("Gemini YouTube 통합 검수 실패. videoId={}", videoId, e);
            throw new BusinessException(ErrorCode.AI_CONTENT_INSPECTION_FAILED);
        }
    }

    private IntegratedInspectionResult map(String json) throws JacksonException {
        RawIntegrated raw = objectMapper.readValue(json, RawIntegrated.class);
        RawExtraction extraction = raw.extraction() == null
                ? RawExtraction.empty() : raw.extraction();
        ContentInsight insight = extraction.insight() == null
                ? ContentInsight.empty()
                : new ContentInsight(
                        value(extraction.insight().contentStyle()),
                        value(extraction.insight().tone()),
                        list(extraction.insight().strengths()),
                        list(extraction.insight().cautions()),
                        list(extraction.insight().risks()),
                        extraction.insight().hateConfirmed(),
                        list(extraction.insight().collabBrands()));
        SttResult sttResult = new SttResult(
                value(extraction.summary()), value(extraction.stt()),
                value(extraction.ocr()), insight);

        Map<String, Object> policyJson = new LinkedHashMap<>();
        policyJson.put("report", raw.report());
        policyJson.put("violations", raw.violations() == null ? List.of() : raw.violations());
        AiInspectionResponse inspection = inspectionMapper.mapResponse(
                objectMapper.writeValueAsString(policyJson));
        return new IntegratedInspectionResult(sttResult, inspection);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> integratedResponseSchema() {
        Map<String, Object> policySchema = inspectionMapper.responseJsonSchema();
        Map<String, Object> policyProperties =
                (Map<String, Object>) policySchema.get("properties");

        Map<String, Object> insight = Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "contentStyle", Map.of("type", "string"),
                        "tone", Map.of("type", "string"),
                        "strengths", stringArray(),
                        "cautions", stringArray(),
                        "risks", stringArray(),
                        "hateConfirmed", Map.of("type", "boolean"),
                        "collabBrands", stringArray()),
                "required", List.of(
                        "contentStyle", "tone", "strengths", "cautions", "risks",
                        "hateConfirmed", "collabBrands"));
        Map<String, Object> extraction = Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "summary", Map.of("type", "string"),
                        "stt", Map.of("type", "string"),
                        "ocr", Map.of("type", "string"),
                        "insight", insight),
                "required", List.of("summary", "stt", "ocr", "insight"));

        Map<String, Object> propertiesMap = new LinkedHashMap<>();
        propertiesMap.put("extraction", extraction);
        propertiesMap.put("report", policyProperties.get("report"));
        propertiesMap.put("violations", policyProperties.get("violations"));
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", propertiesMap,
                "required", List.of("extraction", "report", "violations"));
    }

    private Map<String, Object> stringArray() {
        return Map.of("type", "array", "items", Map.of("type", "string"));
    }

    private String extractText(GeminiResponse response) {
        if (response == null || response.candidates() == null
                || response.candidates().isEmpty()) {
            throw new IllegalArgumentException("Gemini 통합 검수 응답이 비어 있습니다.");
        }
        Candidate candidate = response.candidates().getFirst();
        if (candidate.finishReason() != null && !"STOP".equals(candidate.finishReason())) {
            throw new IllegalArgumentException(
                    "Gemini 통합 검수가 정상 종료되지 않았습니다: " + candidate.finishReason());
        }
        if (candidate.content() == null || candidate.content().parts() == null) {
            throw new IllegalArgumentException("Gemini 통합 검수 콘텐츠가 비어 있습니다.");
        }
        return candidate.content().parts().stream()
                .map(Part::text)
                .filter(java.util.Objects::nonNull)
                .reduce("", String::concat);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static List<String> list(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private record RawIntegrated(
            RawExtraction extraction,
            Object report,
            List<Object> violations) {
    }

    private record RawExtraction(
            String summary,
            String stt,
            String ocr,
            RawInsight insight) {
        private static RawExtraction empty() {
            return new RawExtraction("", "", "", null);
        }
    }

    private record RawInsight(
            String contentStyle,
            String tone,
            List<String> strengths,
            List<String> cautions,
            List<String> risks,
            boolean hateConfirmed,
            List<String> collabBrands) {
    }

    private record GeminiResponse(List<Candidate> candidates) {
    }

    private record Candidate(ResponseContent content, String finishReason) {
    }

    private record ResponseContent(List<Part> parts) {
    }

    private record Part(String text) {
    }
}
