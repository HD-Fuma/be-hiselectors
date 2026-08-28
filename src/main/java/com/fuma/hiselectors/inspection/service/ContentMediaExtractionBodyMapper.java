package com.fuma.hiselectors.inspection.service;

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

    public boolean isCurrentExtraction(Map<String, Object> body) {
        if (body == null || !ContentMediaExtractionResult.CURRENT_SCHEMA_VERSION.equals(
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
