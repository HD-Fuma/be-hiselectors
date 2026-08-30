package com.fuma.hiselectors.inspection.config;

import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "content-inspection.analysis")
public record ContentInspectionAnalysisProperties(
        String apiKey,
        String apiKeys,
        String fallbackModels,
        String model,
        Integer maxOutputTokens
) {
    private static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 8_192;

    public boolean hasApiKey() {
        return !values(apiKey, apiKeys).isEmpty();
    }

    public String modelOrDefault() {
        return model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
    }

    public int maxOutputTokensOrDefault() {
        return maxOutputTokens == null ? DEFAULT_MAX_OUTPUT_TOKENS : maxOutputTokens;
    }

    public List<Attempt> attempts(String primaryModel) {
        LinkedHashSet<String> models = values(primaryModel, fallbackModels);
        models.add(modelOrDefault());
        return values(apiKey, apiKeys).stream()
                .flatMap(key -> models.stream().map(candidate -> new Attempt(candidate, key)))
                .toList();
    }

    private static LinkedHashSet<String> values(String first, String csv) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        add(values, first);
        if (csv != null) {
            for (String value : csv.split(",")) {
                add(values, value);
            }
        }
        return values;
    }

    private static void add(LinkedHashSet<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value.trim());
        }
    }

    public record Attempt(String model, String apiKey) {
    }
}
