package com.fuma.hiselectors.application.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.stt.SttWorkerProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class LocalAnalyzerClient {

    private static final String REPORT_RANKING_QUERY =
            "콘텐츠의 반복 주제, 스타일, 말투와 톤, 강점, 브랜드 협찬, 광고, 정치 종교 논란, "
                    + "건강 주장, 부작용, 선정성, 저작권, 사행성, 욕설과 혐오를 평가";

    private final RestClient restClient;
    private final String baseUrl;

    public LocalAnalyzerClient(SttWorkerProperties properties) {
        this.baseUrl = properties.baseUrlOrDefault();
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

    /** 의미 관련도와 임베딩 MMR 순서. 워커 장애 시 빈 순서로 폴백해 리포트 생성을 막지 않는다. */
    public List<Integer> rankSegments(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        try {
            RankResult result = restClient.post()
                    .uri(baseUrl + "/rank")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("texts", texts, "query", REPORT_RANKING_QUERY,
                            "relevance_weight", 0.7))
                    .retrieve()
                    .body(RankResult.class);
            return result == null || result.order() == null ? List.of() : result.order();
        } catch (RestClientException e) {
            log.warn("임베딩 문장 랭킹 실패. 규칙 기반 순서로 폴백한다. baseUrl={}", baseUrl, e);
            return List.of();
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RankResult(List<Integer> order) { }
}
