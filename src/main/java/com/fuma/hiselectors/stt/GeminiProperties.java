package com.fuma.hiselectors.stt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(String apiKey, String model, String mediaResolution) {

    private static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";
    private static final String DEFAULT_MEDIA_RESOLUTION = "MEDIA_RESOLUTION_LOW";
    //  화면 텍스트 OCR의 높은 품질을 원한다면 MEDIA_RESOLUTION_HIGH

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String modelOrDefault() {
        return model == null || model.isBlank() ? DEFAULT_MODEL : model;
    }

    public String mediaResolutionOrDefault() {
        return mediaResolution == null || mediaResolution.isBlank()
                ? DEFAULT_MEDIA_RESOLUTION : mediaResolution;
    }
}
