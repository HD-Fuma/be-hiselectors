package com.fuma.hiselectors.stt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(String apiKey, String model) {

    private static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String modelOrDefault() {
        return model == null || model.isBlank() ? DEFAULT_MODEL : model;
    }
}
