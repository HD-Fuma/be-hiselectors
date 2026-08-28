package com.fuma.hiselectors.inspection.extraction;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.config.InspectionExtractionProperties;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** 지원서 Gemini 설정과 분리된 콘텐츠 추출 전용 재시도 실행기다. */
@Component
public class ContentGeminiRequestExecutor {

    private final InspectionExtractionProperties properties;

    public ContentGeminiRequestExecutor(InspectionExtractionProperties properties) {
        this.properties = properties;
    }

    public <T> Execution<T> execute(Function<Attempt, T> request) {
        List<Attempt> attempts = attempts();
        if (attempts.isEmpty()) {
            throw new BusinessException(ErrorCode.GEMINI_API_KEY_MISSING);
        }
        RestClientException last = null;
        int attempted = 0;
        for (Attempt attempt : attempts) {
            attempted++;
            try {
                return new Execution<>(request.apply(attempt), attempted, attempt.model());
            } catch (RestClientException exception) {
                last = exception;
                if (!retryable(exception)) {
                    throw exception;
                }
            }
        }
        throw last == null ? new BusinessException(ErrorCode.GEMINI_API_CALL_FAILED) : last;
    }

    private List<Attempt> attempts() {
        InspectionExtractionProperties.Youtube youtube = properties.youtube();
        if (youtube == null) {
            return List.of();
        }
        LinkedHashSet<String> keys = values(youtube.apiKey(), youtube.apiKeys());
        LinkedHashSet<String> models = values(
                properties.youtubeModelOrDefault(), youtube.fallbackModels());
        return keys.stream()
                .flatMap(key -> models.stream().map(model -> new Attempt(model, key)))
                .toList();
    }

    private boolean retryable(RestClientException exception) {
        if (!(exception instanceof RestClientResponseException response)) {
            return true;
        }
        int status = response.getStatusCode().value();
        return status == 401 || status == 403 || status == 408 || status == 429 || status >= 500;
    }

    private LinkedHashSet<String> values(String first, String csv) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        add(values, first);
        if (csv != null) {
            for (String value : csv.split(",")) {
                add(values, value);
            }
        }
        return values;
    }

    private void add(LinkedHashSet<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value.trim());
        }
    }

    public record Attempt(String model, String apiKey) {
    }

    public record Execution<T>(T value, int attemptCount, String selectedModel) {
    }
}
