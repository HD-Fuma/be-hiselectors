package com.fuma.hiselectors.inspection.extraction;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.config.InspectionExtractionProperties;
import com.fuma.hiselectors.inspection.extraction.model.ContentMediaExtractionResult;
import com.fuma.hiselectors.inspection.service.InspectionPromptProvider;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** 공개 YouTube URL에서 정책 판단 없이 STT/OCR/시각 근거만 추출한다. */
@Slf4j
@Component
public class YoutubeContentExtractionClient {

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/%s/interactions";

    private final InspectionExtractionProperties properties;
    private final ContentGeminiRequestExecutor requestExecutor;
    private final InspectionPromptProvider promptProvider;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Autowired
    public YoutubeContentExtractionClient(
            InspectionExtractionProperties properties,
            ContentGeminiRequestExecutor requestExecutor,
            InspectionPromptProvider promptProvider,
            ObjectMapper objectMapper) {
        this(properties, requestExecutor, promptProvider, objectMapper, defaultRestClient());
    }

    YoutubeContentExtractionClient(
            InspectionExtractionProperties properties,
            ContentGeminiRequestExecutor requestExecutor,
            InspectionPromptProvider promptProvider,
            ObjectMapper objectMapper,
            RestClient restClient) {
        this.properties = properties;
        this.requestExecutor = requestExecutor;
        this.promptProvider = promptProvider;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    public ContentExtractionExecutionResult extract(String videoId) {
        if (videoId == null || videoId.isBlank()) {
            throw new IllegalArgumentException("YouTube videoId는 필수입니다.");
        }
        String videoUrl = "https://www.youtube.com/watch?v=" + videoId.strip();
        long started = System.nanoTime();
        try {
            ContentGeminiRequestExecutor.Execution<InteractionResponse> execution =
                    requestExecutor.execute(attempt -> call(attempt, requestBody(
                            attempt.model(), videoUrl)));
            long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
            InteractionResponse response = execution.value();
            String json = extractOutputText(response);
            ContentMediaExtractionResult extraction =
                    objectMapper.readValue(json, ContentMediaExtractionResult.class);
            Usage usage = response.usage();
            return new ContentExtractionExecutionResult(
                    extraction,
                    response.id(),
                    properties.youtubeModelOrDefault(),
                    execution.selectedModel(),
                    response.model(),
                    elapsedMs,
                    execution.attemptCount(),
                    usage == null ? null : usage.totalInputTokens(),
                    usage == null ? null : usage.totalOutputTokens(),
                    usage == null ? null : usage.totalThoughtTokens(),
                    usage == null ? null : usage.totalTokens());
        } catch (RestClientResponseException exception) {
            log.warn("YouTube 콘텐츠 추출 API 오류. status={}, body={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString());
            if (exception.getStatusCode().value() == 429) {
                throw new BusinessException(ErrorCode.AI_CONTENT_INSPECTION_QUOTA_EXCEEDED);
            }
            throw new BusinessException(ErrorCode.AI_CONTENT_INSPECTION_FAILED);
        } catch (RestClientException | JacksonException | IllegalArgumentException exception) {
            log.warn("YouTube 콘텐츠 추출 실패", exception);
            throw new BusinessException(ErrorCode.AI_CONTENT_INSPECTION_FAILED);
        }
    }

    private InteractionResponse call(
            ContentGeminiRequestExecutor.Attempt attempt, Map<String, Object> body) {
        return restClient.post()
                .uri(ENDPOINT.formatted(properties.youtubeApiVersionOrDefault()))
                .header("x-goog-api-key", attempt.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(InteractionResponse.class);
    }

    private Map<String, Object> requestBody(String model, String videoUrl) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", List.of(
                Map.of("type", "video", "uri", videoUrl),
                Map.of("type", "text", "text", promptProvider.youtubeExtractionPrompt())));
        body.put("response_format", Map.of(
                "type", "text",
                "mime_type", "application/json",
                "schema", responseSchema()));
        body.put("generation_config", Map.of(
                "max_output_tokens", properties.youtubeMaxOutputTokensOrDefault()));
        body.put("store", false);
        return body;
    }

    private String extractOutputText(InteractionResponse response) {
        if (response == null || !"completed".equals(response.status())
                || response.steps() == null) {
            throw new IllegalArgumentException(
                    "Gemini Interaction이 완료되지 않았습니다: "
                            + (response == null ? null : response.status()));
        }
        List<String> finalTexts = List.of();
        for (Step step : response.steps()) {
            if (!"model_output".equals(step.type()) || step.content() == null) {
                continue;
            }
            List<String> texts = step.content().stream()
                    .filter(content -> "text".equals(content.type()))
                    .map(Content::text)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (!texts.isEmpty()) {
                finalTexts = texts;
            }
        }
        if (finalTexts.isEmpty()) {
            throw new IllegalArgumentException("Gemini Interaction에 모델 텍스트가 없습니다.");
        }
        return String.join("", finalTexts);
    }

    Map<String, Object> responseSchema() {
        Map<String, Object> timeSegment = new LinkedHashMap<>();
        timeSegment.put("type", "object");
        timeSegment.put("additionalProperties", false);
        timeSegment.put("properties", Map.of(
                "segmentId", Map.of("type", "string"),
                "startMs", Map.of("type", "integer", "minimum", 0),
                "endMs", Map.of("type", "integer", "minimum", 1),
                "text", Map.of("type", "string")));
        timeSegment.put("required", List.of("segmentId", "startMs", "endMs", "text"));

        Map<String, Object> bbox = Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "x", normalizedNumber(false),
                        "y", normalizedNumber(false),
                        "width", normalizedNumber(true),
                        "height", normalizedNumber(true)),
                "required", List.of("x", "y", "width", "height"));

        Map<String, Object> ocrSegment = new LinkedHashMap<>();
        ocrSegment.put("type", "object");
        ocrSegment.put("additionalProperties", false);
        ocrSegment.put("properties", Map.of(
                "segmentId", Map.of("type", "string"),
                "startMs", Map.of("type", List.of("integer", "null"), "minimum", 0),
                "endMs", Map.of("type", List.of("integer", "null"), "minimum", 1),
                "text", Map.of("type", "string"),
                "coordinateSpace", Map.of(
                        "type", "string", "enum", List.of("NORMALIZED")),
                "bbox", bbox));
        ocrSegment.put("required", List.of(
                "segmentId", "startMs", "endMs", "text", "coordinateSpace", "bbox"));

        Map<String, Object> visualSegment = Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "segmentId", Map.of("type", "string"),
                        "startMs", Map.of("type", "integer", "minimum", 0),
                        "endMs", Map.of("type", "integer", "minimum", 1),
                        "description", Map.of("type", "string")),
                "required", List.of("segmentId", "startMs", "endMs", "description"));

        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "schemaVersion", Map.of(
                                "type", "string", "enum", List.of("1.0")),
                        "stt", sectionSchema(Map.of(
                                "language", Map.of("type", "string"),
                                "segments", arrayOf(timeSegment)),
                                List.of("language", "segments")),
                        "ocr", sectionSchema(Map.of(
                                "segments", arrayOf(ocrSegment)), List.of("segments")),
                        "visual", sectionSchema(Map.of(
                                "segments", arrayOf(visualSegment)), List.of("segments"))),
                "required", List.of("schemaVersion", "stt", "ocr", "visual"));
    }

    private Map<String, Object> normalizedNumber(boolean positive) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "number");
        schema.put("minimum", positive ? 0.000001 : 0);
        schema.put("maximum", 1);
        return schema;
    }

    private Map<String, Object> sectionSchema(
            Map<String, Object> properties, List<String> required) {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", properties,
                "required", required);
    }

    private Map<String, Object> arrayOf(Map<String, Object> items) {
        return Map.of("type", "array", "items", items);
    }

    private static RestClient defaultRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofMinutes(10));
        return RestClient.builder().requestFactory(factory).build();
    }

    record InteractionResponse(
            String id,
            String model,
            String status,
            List<Step> steps,
            Usage usage
    ) {
    }

    record Step(String type, List<Content> content) {
    }

    record Content(String type, String text) {
    }

    record Usage(
            @JsonProperty("total_input_tokens") Integer totalInputTokens,
            @JsonProperty("total_output_tokens") Integer totalOutputTokens,
            @JsonProperty("total_thought_tokens") Integer totalThoughtTokens,
            @JsonProperty("total_tokens") Integer totalTokens
    ) {
    }
}
