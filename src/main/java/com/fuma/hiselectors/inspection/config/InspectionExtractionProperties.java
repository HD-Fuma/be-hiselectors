package com.fuma.hiselectors.inspection.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "content-inspection.extraction")
public record InspectionExtractionProperties(
        Instagram instagram
) {
    public record Instagram(String sttModel, String ocrModel) {
    }

    public String instagramSttModelOrDefault() {
        return instagram == null || instagram.sttModel() == null
                || instagram.sttModel().isBlank() ? "not-configured" : instagram.sttModel();
    }

    public String instagramOcrModelOrDefault() {
        return instagram == null || instagram.ocrModel() == null
                || instagram.ocrModel().isBlank() ? "not-configured" : instagram.ocrModel();
    }
}
