package com.fuma.hiselectors.stt;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Instagram 릴스 분석은 파이썬 워커(faster-whisper + RapidOCR)가 담당한다.
 * Graph API media_url/thumbnail_url(공식 API)을 넘기면 워커가 CDN에서 취득·STT·OCR·분석 후
 * 결과만 돌려준다(무저장). 스크래핑(yt-dlp)은 ToS 위반이라 쓰지 않는다.
 */
@Slf4j
@Component
public class InstagramSttClient {

    private final SttWorkerProperties properties;
    private final RestClient restClient;

    public InstagramSttClient(SttWorkerProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        // SageMaker STT 자체가 최대 580초 대기하므로 미디어 취득·OCR까지 포함할 여유를 둔다.
        factory.setReadTimeout(Duration.ofMinutes(15));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * @param mediaUrl     Graph API media_url. 워커가 CDN 직다운(영상/이미지)
     * @param thumbnailUrl Graph API thumbnail_url. media_url 없을 때(저작권 릴스) 폴백
     * @return 취득·STT·OCR·분석 결과. 저장하지 않는다.
     */
    /** STT 워커 생존 확인. GET /health 200 이면 true, 통신 실패/오류면 false. */
    public boolean isHealthy() {
        try {
            restClient.get()
                    .uri(properties.baseUrlOrDefault() + "/health")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            log.info("STT 워커 헬스체크 실패. baseUrl={}", properties.baseUrlOrDefault());
            return false;
        }
    }

    public InstagramAnalysisResult analyze(String mediaUrl, String thumbnailUrl) {
        Map<String, Object> body = new HashMap<>();
        if (mediaUrl != null) {
            body.put("media_url", mediaUrl);
        }
        if (thumbnailUrl != null) {
            body.put("thumbnail_url", thumbnailUrl);
        }
        try {
            InstagramAnalysisResult result = restClient.post()
                    .uri(properties.baseUrlOrDefault() + "/reel")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(InstagramAnalysisResult.class);

            if (result == null) {
                throw new BusinessException(ErrorCode.STT_WORKER_CALL_FAILED);
            }
            return result;
        } catch (RestClientResponseException e) {
            // 410 = media_url 만료(워커가 CdnExpiredError 를 410으로 반환). 재요청 대상으로 구분.
            if (e.getStatusCode().value() == 410) {
                log.info("media_url 만료(410). Graph API 재요청 필요. body={}", e.getResponseBodyAsString());
                throw new BusinessException(ErrorCode.MEDIA_URL_EXPIRED);
            }
            // 그 외 오류 응답: 워커 500 본문(detail)에 실제 원인.
            log.warn("STT 워커 오류 응답. status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.STT_WORKER_CALL_FAILED);
        } catch (RestClientException e) {
            log.warn("STT 워커 통신 실패. baseUrl={}", properties.baseUrlOrDefault(), e);
            throw new BusinessException(ErrorCode.STT_WORKER_CALL_FAILED);
        }
    }
}
