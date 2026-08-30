package com.fuma.hiselectors.inspection.service;

import com.fuma.hiselectors.content.model.ContentReportAnalysis;
import com.fuma.hiselectors.inspection.extraction.model.ContentMediaExtractionResult;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** ContentMedia.body와 콘텐츠 추출 DTO 사이의 단일 JSON 경계를 제공한다. */
@Component
public class ContentMediaExtractionBodyMapper {

    private final ObjectMapper objectMapper;

    public ContentMediaExtractionBodyMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> toBody(ContentMediaExtractionResult extraction) {
        try {
            return objectMapper.readValue(
                    objectMapper.writeValueAsString(extraction),
                    new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (JacksonException exception) {
            throw new IllegalStateException("콘텐츠 추출 결과를 body로 변환할 수 없습니다.", exception);
        }
    }

    public ContentMediaExtractionResult fromBody(Map<String, Object> body) {
        try {
            return objectMapper.readValue(
                    objectMapper.writeValueAsString(body), ContentMediaExtractionResult.class);
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("유효하지 않은 콘텐츠 추출 body입니다.", exception);
        }
    }

    /**
     * 검수 모델에는 판정에 필요한 segmentId와 text만 넘긴다.
     * 시간·bbox는 content_media.body에 남겨 두고 location에서 해석한다.
     */
    public Map<String, Object> toInspectionBody(Map<String, Object> body) {
        if (body == null || body.isEmpty() || !isCurrentExtraction(body)) {
            return body == null ? Map.of() : body;
        }
        ContentMediaExtractionResult extraction = fromBody(body);
        Map<String, Object> inspectionBody = new LinkedHashMap<>();
        inspectionBody.put("schemaVersion", extraction.schemaVersion());
        inspectionBody.put("stt", Map.of(
                "language", extraction.stt().language(),
                "segments", extraction.stt().segments().stream()
                        .map(segment -> Map.<String, Object>of(
                                "segmentId", segment.segmentId(),
                                "text", segment.text()))
                        .toList()));
        inspectionBody.put("ocr", Map.of(
                "segments", extraction.ocr().segments().stream()
                        .map(segment -> Map.<String, Object>of(
                                "segmentId", segment.segmentId(),
                                "text", segment.text()))
                        .toList()));
        return inspectionBody;
    }

    public ContentReportAnalysis reportFrom(Map<String, Object> body) {
        if (body == null || body.isEmpty() || !isCurrentExtraction(body)) {
            return ContentReportAnalysis.empty();
        }
        return fromBody(body).report();
    }

    public boolean isCurrentExtraction(Map<String, Object> body) {
        if (body == null || !ContentMediaExtractionResult.supportsSchemaVersion(
                body.get("schemaVersion"))) {
            return false;
        }
        try {
            fromBody(body);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
