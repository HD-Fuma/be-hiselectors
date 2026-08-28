package com.fuma.hiselectors.inspection.extraction;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.config.InspectionExtractionProperties;
import com.fuma.hiselectors.inspection.extraction.model.ContentMediaExtractionResult;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** 지원서용 /reel과 분리된 콘텐츠 검수 전용 Instagram 추출 클라이언트다. */
@Slf4j
@Component
public class InstagramContentExtractionClient {

    private final InspectionExtractionProperties properties;
    private final RestClient restClient;

    public InstagramContentExtractionClient(InspectionExtractionProperties properties) {
        this(properties, defaultRestClient());
    }

    InstagramContentExtractionClient(
            InspectionExtractionProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    public ContentExtractionExecutionResult extract(String mediaUrl, String thumbnailUrl) {
        Map<String, Object> request = new LinkedHashMap<>();
        putIfPresent(request, "media_url", mediaUrl);
        putIfPresent(request, "thumbnail_url", thumbnailUrl);
        if (request.isEmpty()) {
            throw new IllegalArgumentException("Instagram mediaUrl 또는 thumbnailUrl은 필수입니다.");
        }
        long started = System.nanoTime();
        try {
            ContentMediaExtractionResult extraction = restClient.post()
                    .uri(properties.instagramWorkerBaseUrlOrDefault() + "/content/reel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ContentMediaExtractionResult.class);
            if (extraction == null) {
                throw new BusinessException(ErrorCode.STT_WORKER_CALL_FAILED);
            }
            return new ContentExtractionExecutionResult(
                    extraction, null,
                    properties.instagramSttModelOrDefault(),
                    properties.instagramSttModelOrDefault(),
                    properties.instagramSttModelOrDefault(),
                    Duration.ofNanos(System.nanoTime() - started).toMillis(),
                    1, null, null, null, null);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 410) {
                throw new BusinessException(ErrorCode.MEDIA_URL_EXPIRED);
            }
            log.warn("콘텐츠 전용 Instagram 추출 오류. status={}, body={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.STT_WORKER_CALL_FAILED);
        } catch (RestClientException exception) {
            log.warn("콘텐츠 전용 Instagram 추출 통신 실패", exception);
            throw new BusinessException(ErrorCode.STT_WORKER_CALL_FAILED);
        }
    }

    private void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private static RestClient defaultRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofMinutes(10));
        return RestClient.builder().requestFactory(factory).build();
    }
}
