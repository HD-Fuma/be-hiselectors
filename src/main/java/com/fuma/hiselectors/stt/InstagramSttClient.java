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
 * URL 하나를 넘기면 워커가 직접 취득·STT·OCR·분석 후 결과만 돌려준다(무저장).
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
        // 취득 + STT(수십 초~분) + OCR 을 워커가 동기로 처리하므로 응답 제한을 넉넉히 둔다.
        factory.setReadTimeout(Duration.ofMinutes(10));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** 릴스 permalink만으로 취득(yt-dlp). media_url 없이 부를 때. */
    public InstagramAnalysisResult analyze(String reelUrl) {
        return analyze(reelUrl, null, null);
    }

    /**
     * @param reelUrl      릴스 permalink(yt-dlp 폴백용)
     * @param mediaUrl     Graph API media_url. 있으면 워커가 CDN 직다운(yt-dlp 안 씀)
     * @param thumbnailUrl Graph API thumbnail_url. 영상 취득 실패 시 폴백
     * @return 취득·STT·OCR·분석 결과. 저장하지 않는다.
     */
    public InstagramAnalysisResult analyze(String reelUrl, String mediaUrl, String thumbnailUrl) {
        Map<String, Object> body = new HashMap<>();
        if (reelUrl != null) {
            body.put("url", reelUrl);
        }
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
            // 워커가 반환한 500 본문(detail)에 실제 원인이 담겨 있다.
            log.warn("STT 워커 오류 응답. status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.STT_WORKER_CALL_FAILED);
        } catch (RestClientException e) {
            log.warn("STT 워커 통신 실패. baseUrl={}", properties.baseUrlOrDefault(), e);
            throw new BusinessException(ErrorCode.STT_WORKER_CALL_FAILED);
        }
    }
}
