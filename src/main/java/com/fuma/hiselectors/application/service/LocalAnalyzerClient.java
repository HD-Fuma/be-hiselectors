package com.fuma.hiselectors.application.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class LocalAnalyzerClient {

    private final RestClient restClient;
    private final String baseUrl;

    public LocalAnalyzerClient(@Value("${analyzer.base-url:http://localhost:8900}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.restClient = RestClient.builder()
                .requestFactory(factory())
                .build();
    }

    private static org.springframework.http.client.SimpleClientHttpRequestFactory factory() {
        var f = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(3));
        f.setReadTimeout(Duration.ofSeconds(60));  // 첫 호출은 모델 로딩으로 느릴 수 있음
        return f;
    }

    public LocalAnalysis analyze(String text) {
        try {
            LocalAnalysis result = restClient.post()
                    .uri(baseUrl + "/analyze")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", text == null ? "" : text))
                    .retrieve()
                    .body(LocalAnalysis.class);
            return result == null ? LocalAnalysis.empty() : result;
        } catch (RestClientException e) {
            // 워커 장애를 빈 카테고리(→422)로 삼키지 않는다. 다운스트림 장애는 502로 구분.
            log.warn("로컬 분석 워커 호출 실패({}).", baseUrl, e);
            throw new BusinessException(ErrorCode.ANALYZER_UNAVAILABLE);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LocalAnalysis(List<String> keywords, Category category) {

        public static LocalAnalysis empty() {
            return new LocalAnalysis(List.of(), new Category("", true));
        }

        public List<String> keywordsOrEmpty() {
            return keywords == null ? List.of() : keywords;
        }

        public String categoryLabel() {
            return category == null ? "" : category.label();
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Category(String label, boolean uncertain) {
        }
    }
}
