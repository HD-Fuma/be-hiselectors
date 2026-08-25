package com.fuma.hiselectors.inspection.ai;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.content.model.ContentReportData;
import com.fuma.hiselectors.inspection.model.AiInspectionResponse;
import com.fuma.hiselectors.inspection.model.DetectedViolation;
import com.fuma.hiselectors.inspection.model.EvidenceLocation;
import com.fuma.hiselectors.inspection.model.EvidenceSource;
import com.fuma.hiselectors.inspection.model.InspectionContext;
import com.fuma.hiselectors.inspection.model.InspectionPolicy;
import com.fuma.hiselectors.inspection.model.ViolationEvidence;
import com.fuma.hiselectors.inspection.model.ViolationTypeCode;
import com.fuma.hiselectors.inspection.service.InspectionPromptProvider;
import com.fuma.hiselectors.stt.GeminiProperties;
import com.fuma.hiselectors.stt.GeminiRequestExecutor;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class GeminiAiInspectionClient implements AiInspectionClient {

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    private final GeminiProperties properties;
    private final GeminiRequestExecutor requestExecutor;
    private final ObjectMapper objectMapper;
    private final InspectionPromptProvider promptProvider;
    private final RestClient restClient;

    @Autowired
    public GeminiAiInspectionClient(GeminiProperties properties,
                                    GeminiRequestExecutor requestExecutor, ObjectMapper objectMapper,
                                    InspectionPromptProvider promptProvider) {
        this(properties, requestExecutor, objectMapper, promptProvider, createRestClient());
    }

    GeminiAiInspectionClient(GeminiProperties properties, GeminiRequestExecutor requestExecutor,
                              ObjectMapper objectMapper,
                              InspectionPromptProvider promptProvider, RestClient restClient) {
        this.properties = properties;
        this.requestExecutor = requestExecutor;
        this.objectMapper = objectMapper;
        this.promptProvider = promptProvider;
        this.restClient = restClient;
    }

    private static RestClient createRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofMinutes(2));
        return RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public AiInspectionResponse inspect(InspectionContext context, InspectionPolicy policy) {
        Map<String, Object> input = Map.of(
                "sns", context.content().getSnsCode(),
                "contentUrl", context.content().getContentUrl(),
                "media", context.media().stream().map(media -> Map.of(
                        "contentMediaId", media.getId(),
                        "mediaType", media.getMediaType(),
                        "body", media.bodyOrEmpty())).toList());
        return inspectInput(input, policy.getAiModelName(), policy.getAiPrompt());
    }

    @Override
    public AiInspectionResponse inspectText(String text) {
        Map<String, Object> input = Map.of(
                "sns", "PREVIEW",
                "contentUrl", "",
                "media", List.of(Map.of(
                        "contentMediaId", 0,
                        "mediaType", "TEXT",
                        "body", Map.of("text", text))));
        return inspectInput(input, properties.modelOrDefault(), promptProvider.aiPrompt());
    }

    private AiInspectionResponse inspectInput(Map<String, Object> inputData, String modelName,
                                            String prompt) {
        if (!properties.hasApiKey()) {
            throw new BusinessException(ErrorCode.GEMINI_API_KEY_MISSING);
        }
        try {
            String input = objectMapper.writeValueAsString(inputData);
            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of("parts", List.of(
                            Map.of("text", prompt.formatted(input))))),
                    "generationConfig", Map.of(
                            "responseMimeType", "application/json",
                            "responseJsonSchema", responseJsonSchema(),
                            "maxOutputTokens", properties.maxOutputTokensOrDefault()));
            GeminiResponse response = requestExecutor.execute(modelName, attempt ->
                    restClient.post()
                            .uri(ENDPOINT.formatted(attempt.model()))
                            .header("x-goog-api-key", attempt.apiKey())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(GeminiResponse.class));
            return mapResponse(extractText(response));
        } catch (RestClientResponseException e) {
            log.warn("Gemini 콘텐츠 검수 오류 응답. status={}", e.getStatusCode());
            if (e.getStatusCode().value() == 429) {
                throw new BusinessException(ErrorCode.AI_CONTENT_INSPECTION_QUOTA_EXCEEDED);
            }
            throw new BusinessException(ErrorCode.AI_CONTENT_INSPECTION_FAILED);
        } catch (RestClientException | JacksonException | IllegalArgumentException e) {
            log.warn("Gemini 콘텐츠 검수 실패", e);
            throw new BusinessException(ErrorCode.AI_CONTENT_INSPECTION_FAILED);
        }
    }

    AiInspectionResponse mapResponse(String json) throws JacksonException {
        RawInspection raw = objectMapper.readValue(json, RawInspection.class);
        ContentReportData report = raw.report() == null
                ? ContentReportData.empty()
                : new ContentReportData(raw.report().summary(), raw.report().purpose(),
                        raw.report().flow(), raw.report().overallAssessment());
        List<DetectedViolation> violations = raw.violations() == null ? List.of()
                : raw.violations().stream().map(violation -> new DetectedViolation(
                        ViolationTypeCode.valueOf(violation.violationType()),
                        new ViolationEvidence(violation.reason(), violation.confidence(),
                                violation.locations(), EvidenceSource.AI))).toList();
        return new AiInspectionResponse(report, violations);
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

    Map<String, Object> responseJsonSchema() {
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
                        "excerpt", Map.of("type", "string")),
                "required", List.of("contentMediaId", "mediaType", "excerpt"));

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
