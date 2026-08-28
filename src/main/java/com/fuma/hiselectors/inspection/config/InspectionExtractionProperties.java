package com.fuma.hiselectors.inspection.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "content-inspection.extraction")
public record InspectionExtractionProperties(
        Instagram instagram,
        Youtube youtube
) {
    private static final String DEFAULT_YOUTUBE_MODEL = "gemini-3.6-flash";
    private static final int DEFAULT_YOUTUBE_MAX_OUTPUT_TOKENS = 16_384;

    public record Instagram(String sttModel, String ocrModel, String workerBaseUrl) {
    }

    public record Youtube(
            String apiKey,
            String apiKeys,
            String model,
            String fallbackModels,
            Integer maxOutputTokens,
            String apiVersion
    ) {
    }

    public String instagramSttModelOrDefault() {
        return instagram == null || instagram.sttModel() == null
                || instagram.sttModel().isBlank() ? "not-configured" : instagram.sttModel();
    }

    public String instagramOcrModelOrDefault() {
        return instagram == null || instagram.ocrModel() == null
                || instagram.ocrModel().isBlank() ? "not-configured" : instagram.ocrModel();
    }

    public String instagramWorkerBaseUrlOrDefault() {
        return instagram == null || instagram.workerBaseUrl() == null
                || instagram.workerBaseUrl().isBlank()
                ? "http://127.0.0.1:8900" : instagram.workerBaseUrl().trim();
    }

    public String youtubeModelOrDefault() {
        return youtube == null || youtube.model() == null || youtube.model().isBlank()
                ? DEFAULT_YOUTUBE_MODEL : youtube.model().trim();
    }

    public int youtubeMaxOutputTokensOrDefault() {
        return youtube == null || youtube.maxOutputTokens() == null
                ? DEFAULT_YOUTUBE_MAX_OUTPUT_TOKENS : youtube.maxOutputTokens();
    }

    public String youtubeApiVersionOrDefault() {
        return youtube == null || youtube.apiVersion() == null || youtube.apiVersion().isBlank()
                ? "v1beta" : youtube.apiVersion().trim();
    }
}
