package com.fuma.hiselectors.inspection.ai;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentReportAnalysis;
import com.fuma.hiselectors.content.model.ContentReportTextLimits;
import com.fuma.hiselectors.inspection.model.AiInspectionResponse;
import com.fuma.hiselectors.inspection.model.DetectedViolation;
import com.fuma.hiselectors.inspection.model.EvidenceLocation;
import com.fuma.hiselectors.inspection.model.EvidenceSource;
import com.fuma.hiselectors.inspection.model.InspectionContext;
import com.fuma.hiselectors.inspection.model.InspectionPolicy;
import com.fuma.hiselectors.inspection.model.ViolationEvidence;
import com.fuma.hiselectors.inspection.model.ViolationTypeCode;
import com.fuma.hiselectors.inspection.config.ContentInspectionAnalysisProperties;
import com.fuma.hiselectors.inspection.service.ContentMediaExtractionBodyMapper;
import com.fuma.hiselectors.inspection.service.InspectionPromptProvider;
import java.time.Duration;
import java.util.LinkedHashMap;
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
    private final ContentInspectionAnalysisProperties properties;
    private final ContentInspectionGeminiRequestExecutor requestExecutor;
    private final ObjectMapper objectMapper;
    private final InspectionPromptProvider promptProvider;
    private final ContentMediaExtractionBodyMapper bodyMapper;
    private final RestClient restClient;

    @Autowired
    public GeminiAiInspectionClient(ContentInspectionAnalysisProperties properties,
                                    ContentInspectionGeminiRequestExecutor requestExecutor,
                                    ObjectMapper objectMapper,
                                    InspectionPromptProvider promptProvider,
                                    ContentMediaExtractionBodyMapper bodyMapper) {
        this(properties, requestExecutor, objectMapper, promptProvider, bodyMapper,
                createRestClient());
    }

    GeminiAiInspectionClient(
                              ContentInspectionAnalysisProperties properties,
                              ContentInspectionGeminiRequestExecutor requestExecutor,
                              ObjectMapper objectMapper,
                              InspectionPromptProvider promptProvider,
                              ContentMediaExtractionBodyMapper bodyMapper,
                              RestClient restClient) {
        this.properties = properties;
        this.requestExecutor = requestExecutor;
        this.objectMapper = objectMapper;
        this.promptProvider = promptProvider;
        this.bodyMapper = bodyMapper;
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
        return inspectInput(
                mediaInput(context), policy.getAiModelName(), policy.getAiPrompt(),
                policy.getAiPromptVersion(), policy.getId());
    }

    private Map<String, Object> inspectionMedia(ContentMedia media) {
        return Map.of(
                "contentMediaId", media.getId(),
                "mediaType", media.getMediaType(),
                "body", bodyMapper.toInspectionBody(media.bodyOrEmpty()));
    }

    @Override
    public AiInspectionResponse inspectText(String text) {
        return inspectInput(
                textInput(text), properties.modelOrDefault(), promptProvider.aiPrompt(),
                InspectionPromptProvider.AI_PROMPT_VERSION, null);
    }

    @Override
    public ContentReportAnalysis generateReport(InspectionContext context) {
        return generateReportInput(mediaInput(context));
    }

    @Override
    public ContentReportAnalysis generateReportFromText(String text) {
        return generateReportInput(textInput(text));
    }

    private Map<String, Object> mediaInput(InspectionContext context) {
        return Map.of(
                "sns", context.content().getSnsCode(),
                "contentUrl", context.content().getContentUrl(),
                "media", context.media().stream()
                        .map(this::inspectionMedia)
                        .toList());
    }

    private Map<String, Object> textInput(String text) {
        return Map.of(
                "sns", "PREVIEW",
                "contentUrl", "",
                "media", List.of(Map.of(
                        "contentMediaId", 0,
                        "mediaType", "TEXT",
                        "body", Map.of("text", text))));
    }

    private AiInspectionResponse inspectInput(
            Map<String, Object> inputData,
            String modelName,
            String prompt,
            String promptVersion,
            Long inspectionPolicyId) {
        if (!properties.hasApiKey()) {
            throw new BusinessException(ErrorCode.GEMINI_API_KEY_MISSING);
        }
        long started = System.nanoTime();
        try {
            GeminiResponse response = execute(
                    modelName, prompt.formatted(objectMapper.writeValueAsString(inputData)),
                    responseJsonSchema());
            AiInspectionResponse parsed = mapResponse(extractText(response));
            return new AiInspectionResponse(
                    parsed.report(), parsed.violations(), executionMetadata(
                            modelName, promptVersion, inspectionPolicyId, response,
                            Duration.ofNanos(System.nanoTime() - started).toMillis()));
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

    private ContentReportAnalysis generateReportInput(Map<String, Object> inputData) {
        if (!properties.hasApiKey()) {
            throw new BusinessException(ErrorCode.GEMINI_API_KEY_MISSING);
        }
        try {
            GeminiResponse response = execute(
                    properties.modelOrDefault(),
                    promptProvider.contentReportPrompt()
                            .formatted(objectMapper.writeValueAsString(inputData)),
                    reportResponseSchema());
            return toReport(objectMapper.readValue(
                    extractText(response), RawGeneratedReport.class).report());
        } catch (RestClientResponseException e) {
            log.warn("Gemini 콘텐츠 리포트 오류 응답. status={}", e.getStatusCode());
            if (e.getStatusCode().value() == 429) {
                throw new BusinessException(ErrorCode.AI_CONTENT_INSPECTION_QUOTA_EXCEEDED);
            }
            throw new BusinessException(ErrorCode.AI_CONTENT_INSPECTION_FAILED);
        } catch (RestClientException | JacksonException | IllegalArgumentException e) {
            log.warn("Gemini 콘텐츠 리포트 실패", e);
            throw new BusinessException(ErrorCode.AI_CONTENT_INSPECTION_FAILED);
        }
    }

    private GeminiResponse execute(
            String modelName, String prompt, Map<String, Object> schema) {
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(
                        Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseJsonSchema", schema,
                        "maxOutputTokens", properties.maxOutputTokensOrDefault()));
        return requestExecutor.execute(modelName, attempt ->
                restClient.post()
                        .uri(ENDPOINT.formatted(attempt.model()))
                        .header("x-goog-api-key", attempt.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(GeminiResponse.class));
    }

    AiInspectionResponse mapResponse(String json) throws JacksonException {
        RawInspection raw = objectMapper.readValue(json, RawInspection.class);
        ContentReportAnalysis report = ContentReportAnalysis.empty();
        List<DetectedViolation> violations = raw.violations() == null ? List.of()
                : raw.violations().stream().map(violation -> new DetectedViolation(
                        ViolationTypeCode.valueOf(violation.violationType()),
                        new ViolationEvidence(
                                ContentReportTextLimits.clip(
                                        violation.reason(), ContentReportTextLimits.REASON),
                                violation.confidence(),
                                violation.locations(), EvidenceSource.AI))).toList();
        return new AiInspectionResponse(report, violations);
    }

    private ContentReportAnalysis toReport(RawReport raw) {
        if (raw == null) {
            return ContentReportAnalysis.empty();
        }
        RawOverview overview = raw.overview();
        RawInsight insight = raw.insight();
        return new ContentReportAnalysis(
                overview == null ? ContentReportAnalysis.Overview.empty()
                        : new ContentReportAnalysis.Overview(
                                overview.summary(), overview.purpose(), overview.flow(),
                                overview.overallAssessment()),
                insight == null ? ContentReportAnalysis.Insight.empty()
                        : new ContentReportAnalysis.Insight(
                                insight.contentStyle(), insight.tone(), insight.strengths(),
                                insight.cautions(), insight.risks(), insight.hateConfirmed(),
                                insight.collabBrands()));
    }

    private Map<String, Object> executionMetadata(
            String requestedModel,
            String promptVersion,
            Long inspectionPolicyId,
            GeminiResponse response,
            long latencyMs) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", "GEMINI");
        metadata.put("requestedModel", requestedModel);
        metadata.put("promptVersion", promptVersion);
        if (inspectionPolicyId != null) {
            metadata.put("inspectionPolicyId", inspectionPolicyId);
        }
        metadata.put("latencyMs", latencyMs);
        if (response != null && response.modelVersion() != null) {
            metadata.put("responseModel", response.modelVersion());
        }
        if (response != null && response.usageMetadata() != null) {
            UsageMetadata usage = response.usageMetadata();
            Map<String, Object> tokens = new LinkedHashMap<>();
            putIfPresent(tokens, "input", usage.promptTokenCount());
            putIfPresent(tokens, "output", usage.candidatesTokenCount());
            putIfPresent(tokens, "thought", usage.thoughtsTokenCount());
            putIfPresent(tokens, "total", usage.totalTokenCount());
            if (!tokens.isEmpty()) {
                metadata.put("tokens", tokens);
            }
        }
        return metadata;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
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
        Map<String, Object> location = Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "contentMediaId", Map.of("type", "integer"),
                        "mediaType", Map.of(
                                "type", "string",
                                "enum", List.of("TEXT", "IMAGE", "VIDEO")),
                        "targetKind", Map.of(
                                "type", "string",
                                "enum", List.of(
                                        "TEXT_BODY", "STT_SEGMENT", "OCR_SEGMENT", "MEDIA")),
                        "coordinateSpace", Map.of(
                                "type", "string",
                                "enum", List.of(
                                        "UTF16_CODE_UNIT", "CONTENT_MEDIA_SEGMENT", "NONE")),
                        "segmentId", Map.of("type", "string"),
                        "startIndex", Map.of("type", "integer"),
                        "endIndex", Map.of("type", "integer"),
                        "excerpt", Map.of("type", "string")),
                "required", List.of(
                        "contentMediaId", "mediaType", "targetKind",
                        "coordinateSpace", "excerpt"));

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
                        "reason", limitedString(ContentReportTextLimits.REASON),
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
                        "violations", Map.of(
                                "type", "array",
                                "items", violation)),
                "required", List.of("violations"));
    }

    private Map<String, Object> reportResponseSchema() {
        Map<String, Object> overview = Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "summary", limitedString(ContentReportTextLimits.SUMMARY),
                        "purpose", limitedString(ContentReportTextLimits.PURPOSE),
                        "flow", limitedString(ContentReportTextLimits.FLOW),
                        "overallAssessment", limitedString(
                                ContentReportTextLimits.OVERALL_ASSESSMENT)),
                "required", List.of("summary", "purpose", "flow", "overallAssessment"));
        Map<String, Object> stringArray = Map.of(
                "type", "array",
                "items", limitedString(ContentReportTextLimits.INSIGHT_ITEM));
        Map<String, Object> insight = Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "contentStyle", limitedString(ContentReportTextLimits.CONTENT_STYLE),
                        "tone", limitedString(ContentReportTextLimits.TONE),
                        "strengths", stringArray,
                        "cautions", stringArray,
                        "risks", stringArray,
                        "hateConfirmed", Map.of("type", "boolean"),
                        "collabBrands", stringArray),
                "required", List.of(
                        "contentStyle", "tone", "strengths", "cautions", "risks",
                        "hateConfirmed", "collabBrands"));
        Map<String, Object> report = Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of("overview", overview, "insight", insight),
                "required", List.of("overview", "insight"));
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of("report", report),
                "required", List.of("report"));
    }

    private static Map<String, Object> limitedString(int maxLength) {
        return Map.of("type", "string", "maxLength", maxLength);
    }

    private record RawInspection(List<RawViolation> violations) { }

    private record RawGeneratedReport(RawReport report) { }

    private record RawReport(RawOverview overview, RawInsight insight) { }

    private record RawOverview(
            String summary, String purpose, String flow, String overallAssessment) { }

    private record RawInsight(
            String contentStyle,
            String tone,
            List<String> strengths,
            List<String> cautions,
            List<String> risks,
            boolean hateConfirmed,
            List<String> collabBrands) { }

    private record RawViolation(String violationType, String reason, Double confidence,
                                List<EvidenceLocation> locations) { }

    private record GeminiResponse(
            List<Candidate> candidates,
            String modelVersion,
            UsageMetadata usageMetadata) { }

    private record UsageMetadata(
            Integer promptTokenCount,
            Integer candidatesTokenCount,
            Integer thoughtsTokenCount,
            Integer totalTokenCount) { }

    private record Candidate(ResponseContent content) { }

    private record ResponseContent(List<Part> parts) { }

    private record Part(String text) { }
}
