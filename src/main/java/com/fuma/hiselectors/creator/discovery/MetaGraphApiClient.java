package com.fuma.hiselectors.creator.discovery;

import com.fuma.hiselectors.creator.discovery.dto.InstagramBusinessDiscoveryResponse;
import com.fuma.hiselectors.creator.discovery.dto.InstagramBusinessDiscoveryResponse.BusinessDiscovery;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.net.URI;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
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
                .queryParam("access_token", properties.accessToken())
                .build()
                .encode()
                .toUri();

        try {
            InstagramBusinessDiscoveryResponse response = restClient.get()
                    .uri(uri)
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
        String message = safeErrorMessage(responseBody).toLowerCase();

        boolean unavailableAccount = "10".equals(code)
                || message.contains("not an instagram business")
                || message.contains("cannot be found")
                || message.contains("does not exist")
                || message.contains("unsupported get request");

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
