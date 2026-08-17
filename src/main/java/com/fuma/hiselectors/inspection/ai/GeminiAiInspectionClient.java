package com.fuma.hiselectors.inspection.ai;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.model.AiInspectionResult;
import com.fuma.hiselectors.inspection.model.ContentReportData;
import com.fuma.hiselectors.inspection.model.DetectedViolation;
import com.fuma.hiselectors.inspection.model.EvidenceLocation;
import com.fuma.hiselectors.inspection.model.InspectionContext;
import com.fuma.hiselectors.inspection.model.ViolationEvidence;
import com.fuma.hiselectors.inspection.model.ViolationTypeCode;
import com.fuma.hiselectors.stt.GeminiProperties;
import java.time.Duration;
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

@Slf4j
@Component
public class GeminiAiInspectionClient implements AiInspectionClient {

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    private static final String PROMPT = """
            당신은 현대백화점 셀렉터스 콘텐츠 검수자입니다.
            입력 콘텐츠에서 다음 유형만 판단하세요:
            ABUSIVE_LANGUAGE, HATE_DISCRIMINATION, VIOLENCE_THREAT, SEXUAL_CONTENT,
            POLITICAL_CONTENT, SOCIAL_CONTROVERSY, FALSE_EXAGGERATED_CLAIM,
            BRAND_REPUTATION_DAMAGE.
            광고 표시와 제휴 링크는 별도 규칙이 검사하므로 반환하지 마세요.
            반드시 아래 JSON 형태로만 응답하세요.
            {
              "report": {
                "summary": "콘텐츠 요약",
                "purpose": "콘텐츠 목적",
                "flow": "콘텐츠 흐름",
                "overallAssessment": "전체 판단"
              },
              "violations": [{
                "violationType": "위 유형 중 하나",
                "reason": "판단 근거",
                "confidence": 0.0,
                "locations": []
              }]
            }
            locations는 문자열 배열이 아니라 반드시 객체 배열로 반환하세요.
            텍스트 근거는 다음 형태를 사용하세요.
            [{
              "contentMediaId": 1,
              "mediaType": "TEXT",
              "startIndex": 0,
              "endIndex": 10,
              "excerpt": "위반에 해당하는 원문"
            }]
            locations 객체에는 contentMediaId, mediaType, startIndex, endIndex,
            startTime, endTime, bbox, excerpt 필드를 사용할 수 있습니다.
            값이 없는 선택 필드는 생략하고, 정확한 위치를 판단할 수 없으면
            locations를 빈 배열로 반환하세요. locations에 문자열을 직접 넣지 마세요.

            검수 대상:
            %s
            """;

    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GeminiAiInspectionClient(GeminiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofMinutes(2));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public AiInspectionResult inspect(InspectionContext context) {
        Map<String, Object> input = Map.of(
                "sns", context.content().getSnsCode(),
                "contentUrl", context.content().getContentUrl(),
                "media", context.media().stream().map(media -> Map.of(
                        "contentMediaId", media.getId(),
                        "mediaType", media.getMediaType(),
                        "body", media.bodyOrEmpty())).toList());
        return inspectInput(input);
    }

    @Override
    public AiInspectionResult inspectText(String text) {
        Map<String, Object> input = Map.of(
                "sns", "PREVIEW",
                "contentUrl", "",
                "media", List.of(Map.of(
                        "contentMediaId", 0,
                        "mediaType", "TEXT",
                        "body", Map.of("text", text))));
        return inspectInput(input);
    }

    private AiInspectionResult inspectInput(Map<String, Object> inputData) {
        if (!properties.hasApiKey()) {
            throw new BusinessException(ErrorCode.GEMINI_API_KEY_MISSING);
        }
        try {
            String input = objectMapper.writeValueAsString(inputData);
            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of("parts", List.of(
                            Map.of("text", PROMPT.formatted(input))))),
                    "generationConfig", Map.of(
                            "responseMimeType", "application/json",
                            "responseJsonSchema", responseJsonSchema(),
                            "maxOutputTokens", properties.maxOutputTokensOrDefault()));
            GeminiResponse response = restClient.post()
                    .uri(ENDPOINT.formatted(properties.modelOrDefault()))
                    .header("x-goog-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(GeminiResponse.class);
            return mapResponse(extractText(response));
        } catch (RestClientException | JacksonException | IllegalArgumentException e) {
            log.warn("Gemini 콘텐츠 검수 실패", e);
            throw new BusinessException(ErrorCode.AI_CONTENT_INSPECTION_FAILED);
        }
    }

    private AiInspectionResult mapResponse(String json) throws JacksonException {
        RawInspection raw = objectMapper.readValue(json, RawInspection.class);
        ContentReportData report = raw.report() == null
                ? ContentReportData.empty()
                : new ContentReportData(raw.report().summary(), raw.report().purpose(),
                        raw.report().flow(), raw.report().overallAssessment());
        List<DetectedViolation> violations = raw.violations() == null ? List.of()
                : raw.violations().stream().map(violation -> new DetectedViolation(
                        ViolationTypeCode.valueOf(violation.violationType()),
                        new ViolationEvidence(violation.reason(), violation.confidence(),
                                violation.locations()))).toList();
        return new AiInspectionResult(report, violations);
    }

    private String extractText(GeminiResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()
                || response.candidates().getFirst().content() == null
                || response.candidates().getFirst().content().parts() == null
                || response.candidates().getFirst().content().parts().isEmpty()) {
            throw new IllegalArgumentException("Gemini 검수 응답이 비어 있습니다.");
        }
        return response.candidates().getFirst().content().parts().stream()
                .map(Part::text).filter(java.util.Objects::nonNull)
                .reduce("", String::concat);
    }

    private Map<String, Object> responseJsonSchema() {
        Map<String, Object> boundingBox = Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "x", Map.of("type", "integer"),
                        "y", Map.of("type", "integer"),
                        "width", Map.of("type", "integer"),
                        "height", Map.of("type", "integer")),
                "required", List.of("x", "y", "width", "height"));

        Map<String, Object> location = Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "contentMediaId", Map.of("type", "integer"),
                        "mediaType", Map.of(
                                "type", "string",
                                "enum", List.of("TEXT", "IMAGE", "VIDEO")),
                        "startIndex", Map.of("type", "integer"),
                        "endIndex", Map.of("type", "integer"),
                        "startTime", Map.of("type", "number"),
                        "endTime", Map.of("type", "number"),
                        "bbox", boundingBox,
                        "excerpt", Map.of("type", "string")));

        Map<String, Object> report = Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "summary", Map.of("type", "string"),
                        "purpose", Map.of("type", "string"),
                        "flow", Map.of("type", "string"),
                        "overallAssessment", Map.of("type", "string")),
                "required", List.of("summary", "purpose", "flow", "overallAssessment"));

        Map<String, Object> violation = Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "violationType", Map.of(
                                "type", "string",
                                "enum", List.of(
                                        "ABUSIVE_LANGUAGE",
                                        "HATE_DISCRIMINATION",
                                        "VIOLENCE_THREAT",
                                        "SEXUAL_CONTENT",
                                        "POLITICAL_CONTENT",
                                        "SOCIAL_CONTROVERSY",
                                        "FALSE_EXAGGERATED_CLAIM",
                                        "BRAND_REPUTATION_DAMAGE")),
                        "reason", Map.of("type", "string"),
                        "confidence", Map.of(
                                "type", "number",
                                "minimum", 0,
                                "maximum", 1),
                        "locations", Map.of(
                                "type", "array",
                                "items", location)),
                "required", List.of(
                        "violationType", "reason", "confidence", "locations"));

        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "report", report,
                        "violations", Map.of(
                                "type", "array",
                                "items", violation)),
                "required", List.of("report", "violations"));
    }

    private record RawInspection(RawReport report, List<RawViolation> violations) { }

    private record RawReport(String summary, String purpose, String flow,
                             String overallAssessment) { }

    private record RawViolation(String violationType, String reason, Double confidence,
                                List<EvidenceLocation> locations) { }

    private record GeminiResponse(List<Candidate> candidates) { }

    private record Candidate(ResponseContent content) { }

    private record ResponseContent(List<Part> parts) { }

    private record Part(String text) { }
}
