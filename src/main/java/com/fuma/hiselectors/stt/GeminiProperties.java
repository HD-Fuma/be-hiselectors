package com.fuma.hiselectors.stt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(
        String apiKey,
        String model,
        String youtubeModel,
        String reportModel,
        MediaResolution mediaResolution,
        Integer maxOutputTokens) {

    private static final String DEFAULT_MODEL = "gemini-3.6-flash";
    private static final String DEFAULT_YOUTUBE_MODEL = "gemini-2.5-flash-lite";
    private static final String DEFAULT_REPORT_MODEL = "gemini-3.6-flash";
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 8192;

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
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
}
