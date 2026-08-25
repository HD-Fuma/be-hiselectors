package com.fuma.hiselectors.stt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.List;

@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(
        String apiKey,
        String apiKeys,
        String fallbackModels,
        String model,
        String youtubeModel,
        String reportModel,
        MediaResolution mediaResolution,
        Integer maxOutputTokens) {

    private static final String DEFAULT_MODEL = "gemini-3.6-flash";
    private static final String DEFAULT_YOUTUBE_MODEL = "gemini-3.5-flash-lite";
    private static final String DEFAULT_REPORT_MODEL = "gemini-3.5-flash-lite";
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 8192;

    public boolean hasApiKey() {
        return !values(apiKey, apiKeys).isEmpty();
    }

    public String modelOrDefault() {
        return model == null || model.isBlank() ? DEFAULT_MODEL : model;
    }

    public String youtubeModelOrDefault() {
        return youtubeModel == null || youtubeModel.isBlank()
                ? DEFAULT_YOUTUBE_MODEL : youtubeModel;
    }

    public String reportModelOrDefault() {
        return reportModel == null || reportModel.isBlank()
                ? DEFAULT_REPORT_MODEL : reportModel;
    }

    public String mediaResolutionApiValue() {
        return (mediaResolution == null ? MediaResolution.LOW : mediaResolution).toApiValue();
    }

    public int maxOutputTokensOrDefault() {
        return maxOutputTokens == null ? DEFAULT_MAX_OUTPUT_TOKENS : maxOutputTokens;
    }

    /** 같은 키에서 모든 모델을 시도한 뒤 다음 키로 넘어간다. */
    public List<Attempt> attempts(String primaryModel) {
        LinkedHashSet<String> models = new LinkedHashSet<>();
        models.addAll(values(primaryModel, fallbackModels));
        models.add(modelOrDefault());
        models.add(youtubeModelOrDefault());
        models.add(reportModelOrDefault());

        return values(apiKey, apiKeys).stream()
                .flatMap(key -> models.stream().map(candidate -> new Attempt(candidate, key)))
                .toList();
    }

    private static List<String> values(String first, String csv) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        add(values, first);
        if (csv != null) {
            for (String value : csv.split(",")) {
                add(values, value);
            }
        }
        return List.copyOf(values);
    }

    private static void add(LinkedHashSet<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value.trim());
        }
    }

    public record Attempt(String model, String apiKey) { }
}
