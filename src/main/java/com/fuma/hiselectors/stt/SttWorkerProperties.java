package com.fuma.hiselectors.stt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 파이썬 STT/OCR 워커(Instagram 취득·분석) 접속 설정. */
@ConfigurationProperties(prefix = "stt.worker")
public record SttWorkerProperties(String baseUrl) {

    private static final String DEFAULT_BASE_URL = "http://127.0.0.1:8900";

    public String baseUrlOrDefault() {
        return baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl;
    }
}
