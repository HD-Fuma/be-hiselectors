package com.fuma.hiselectors.creator.discovery;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fuma.hiselectors.creator.discovery.dto.InstagramBusinessDiscoveryResponse;
import com.fuma.hiselectors.creator.discovery.dto.InstagramBusinessDiscoveryResponse.BusinessDiscovery;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.net.URI;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/** Meta Graph API의 business_discovery를 이용해 공개 Instagram 지표를 조회한다. */
@Slf4j
@Component
public class MetaGraphApiClient {

    private static final int DEFAULT_MEDIA_LIMIT = 5;
    private static final int MAX_MEDIA_LIMIT = 25;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9._]{1,30}");
    private static final Pattern ERROR_CODE_PATTERN =
            Pattern.compile("\\\"code\\\"\\s*:\\s*(\\d+)");
    private static final Pattern ERROR_SUBCODE_PATTERN =
            Pattern.compile("\\\"error_subcode\\\"\\s*:\\s*(\\d+)");
    private static final Pattern ERROR_MESSAGE_PATTERN =
            Pattern.compile("\\\"message\\\"\\s*:\\s*\\\"([^\\\"]*)");

    private final MetaGraphProperties properties;
    private final RestClient restClient;

    public MetaGraphApiClient(MetaGraphProperties properties, RestClient oauthRestClient) {
        this.properties = properties;
        this.restClient = oauthRestClient;
    }

    public BusinessDiscovery discover(String instagramHandle) {
        return discover(instagramHandle, DEFAULT_MEDIA_LIMIT);
    }

    public BusinessDiscovery discover(String instagramHandle, int mediaLimit) {
        if (!properties.isConfigured()) {
            throw new BusinessException(ErrorCode.META_GRAPH_CONFIG_MISSING);
        }

        String username = normalizeUsername(instagramHandle);
        int safeMediaLimit = Math.max(1, Math.min(mediaLimit, MAX_MEDIA_LIMIT));
        String fields = fields(username, safeMediaLimit);
        URI uri = UriComponentsBuilder
                .fromUriString(properties.baseUrlOrDefault())
                .pathSegment(properties.apiVersionOrDefault(), properties.igUserId())
                .queryParam("fields", fields)
                .build()
                .encode()
                .toUri();

        try {
            InstagramBusinessDiscoveryResponse response = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken())
                    .retrieve()
                    .body(InstagramBusinessDiscoveryResponse.class);

            if (response == null || response.businessDiscovery() == null) {
                throw new BusinessException(ErrorCode.INSTAGRAM_DISCOVERY_ACCOUNT_NOT_FOUND);
            }
            return response.businessDiscovery();
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            log.warn("Meta Graph API 응답 오류. username={}, status={}, code={}, message={}",
                    username,
                    e.getStatusCode(),
                    extract(ERROR_CODE_PATTERN, responseBody),
                    safeErrorMessage(responseBody));
            throw new BusinessException(errorCodeFor(responseBody));
        } catch (RestClientException e) {
            log.warn("Meta Graph API 통신 실패. username={}", username, e);
            throw new BusinessException(ErrorCode.META_GRAPH_API_CALL_FAILED);
        }
    }

    /**
     * media id 로 fresh media_url/thumbnail_url 재취득(만료된 CDN URL 갱신용).
     * 토큰이 그 미디어를 조회할 수 있어야 함(오운드/접근가능 미디어). mediaId 는 shortcode 아닌 실제 media id.
     */
    public MediaUrls fetchMediaUrls(String mediaId) {
        if (!properties.isConfigured()) {
            throw new BusinessException(ErrorCode.META_GRAPH_CONFIG_MISSING);
        }
        URI uri = UriComponentsBuilder
                .fromUriString(properties.baseUrlOrDefault())
                .pathSegment(properties.apiVersionOrDefault(), mediaId)
                .queryParam("fields", "media_url,thumbnail_url")
                .build()
                .encode()
                .toUri();
        try {
            MediaResponse r = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken())
                    .retrieve()
                    .body(MediaResponse.class);
            if (r == null) {
                throw new BusinessException(ErrorCode.META_GRAPH_API_CALL_FAILED);
            }
            return new MediaUrls(r.mediaUrl(), r.thumbnailUrl());
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientResponseException e) {
            log.warn("media 재취득 실패. mediaId={}, status={}, body={}",
                    mediaId, e.getStatusCode(), safeErrorMessage(e.getResponseBodyAsString()));
            throw new BusinessException(ErrorCode.META_GRAPH_API_CALL_FAILED);
        } catch (RestClientException e) {
            log.warn("media 재취득 통신 실패. mediaId={}", mediaId, e);
            throw new BusinessException(ErrorCode.META_GRAPH_API_CALL_FAILED);
        }
    }

    /** fresh 미디어 URL 쌍. */
    public record MediaUrls(String mediaUrl, String thumbnailUrl) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MediaResponse(@JsonProperty("media_url") String mediaUrl,
                         @JsonProperty("thumbnail_url") String thumbnailUrl) { }

    private String normalizeUsername(String instagramHandle) {
        String username = instagramHandle == null
                ? ""
                : instagramHandle.trim().replaceFirst("^@", "");

        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "Instagram 사용자명이 올바르지 않습니다."
            );
        }
        return username;
    }

    private String fields(String username, int mediaLimit) {
        return "business_discovery.username(" + username + "){"
                + "id,username,name,biography,profile_picture_url,followers_count,media_count,"
                + "media.limit(" + mediaLimit + "){"
                + "id,caption,media_type,permalink,timestamp,like_count,comments_count"
                + "}}";
    }

    /** 조회 불가능한 프로필 오류와 토큰·권한·요청 구성 오류를 구분한다. */
    private ErrorCode errorCodeFor(String responseBody) {
        String code = extract(ERROR_CODE_PATTERN, responseBody);
        String subcode = extract(ERROR_SUBCODE_PATTERN, responseBody);

        // Graph API v26.0에서 business_discovery 대상이 프로 계정이 아닐 때의 서명.
        // code=10은 권한 오류이므로 계정 없음으로 오인하지 않는다.
        boolean unavailableAccount = "110".equals(code) && "2207013".equals(subcode);

        return unavailableAccount
                ? ErrorCode.INSTAGRAM_DISCOVERY_ACCOUNT_NOT_FOUND
                : ErrorCode.META_GRAPH_API_CALL_FAILED;
    }

    private String safeErrorMessage(String responseBody) {
        String message = extract(ERROR_MESSAGE_PATTERN, responseBody);
        if (message.isBlank()) {
            return "응답 메시지 없음";
        }
        String singleLine = message.replaceAll("[\\r\\n\\t]", " ");
        return singleLine.length() <= 300 ? singleLine : singleLine.substring(0, 300);
    }

    private String extract(Pattern pattern, String value) {
        if (value == null) {
            return "";
        }
        java.util.regex.Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : "";
    }
}
