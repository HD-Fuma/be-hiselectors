package com.fuma.hiselectors.youtube.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.youtube.config.YouTubeOAuthProperties;
import com.fuma.hiselectors.youtube.dto.GoogleTokenResponse;
import com.fuma.hiselectors.youtube.dto.YouTubeChannelListResponse;
import com.fuma.hiselectors.youtube.dto.YouTubeVerifyResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class YouTubeOAuthService {

    private static final String GOOGLE_AUTH_URI = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String YOUTUBE_CHANNELS_URI =
            "https://www.googleapis.com/youtube/v3/channels";

    private final YouTubeOAuthProperties properties;
    private final YouTubeOAuthStateProvider stateProvider;
    private final RestClient restClient;

    public YouTubeOAuthService(YouTubeOAuthProperties properties,
                               YouTubeOAuthStateProvider stateProvider) {
        this.properties = properties;
        this.stateProvider = stateProvider;
        this.restClient = RestClient.create();
    }

    public String buildAuthorizationUrl(String hiId) {
        String state = stateProvider.create(hiId);
        return UriComponentsBuilder.fromUriString(GOOGLE_AUTH_URI)
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", properties.scope())
                .queryParam("state", state)
                .queryParam("access_type", "online")   // 소유 확인만 → 리프레시 토큰 불필요
                .queryParam("include_granted_scopes", "true")
                .queryParam("prompt", "select_account") // 사용자가 인증할 채널 계정을 직접 선택
                .build()
                .encode()
                .toUriString();
    }

    public YouTubeVerifyResponse verifyChannelOwnership(String code, String state, String requesterHiId) {
        String stateHiId = resolveHiId(state);
        if (!stateHiId.equals(requesterHiId)) {
            throw new BusinessException(ErrorCode.YOUTUBE_STATE_INVALID);
        }

        String accessToken = exchangeCodeForAccessToken(code);
        YouTubeChannelListResponse.Item channel = fetchMyChannel(accessToken);

        String title = channel.snippet() != null ? channel.snippet().title() : null;
        Long followerCount = extractSubscriberCount(channel);
        return YouTubeVerifyResponse.of(channel.id(), title, followerCount);
    }

    private String resolveHiId(String state) {
        try {
            return stateProvider.resolveHiId(state);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.YOUTUBE_STATE_INVALID);
        }
    }

    private String exchangeCodeForAccessToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", properties.redirectUri());
        form.add("grant_type", "authorization_code");

        GoogleTokenResponse token;
        try {
            token = restClient.post()
                    .uri(GOOGLE_TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(GoogleTokenResponse.class);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.YOUTUBE_OAUTH_FAILED);
        }

        if (token == null || token.accessToken() == null) {
            throw new BusinessException(ErrorCode.YOUTUBE_OAUTH_FAILED);
        }
        return token.accessToken();
    }

    private YouTubeChannelListResponse.Item fetchMyChannel(String accessToken) {
        YouTubeChannelListResponse response;
        try {
            response = restClient.get()
                    .uri(UriComponentsBuilder.fromUriString(YOUTUBE_CHANNELS_URI)
                            .queryParam("part", "snippet,statistics")
                            .queryParam("mine", "true")
                            .build(true)
                            .toUri())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(YouTubeChannelListResponse.class);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.YOUTUBE_OAUTH_FAILED);
        }

        if (response == null || response.items() == null || response.items().isEmpty()) {
            throw new BusinessException(ErrorCode.YOUTUBE_CHANNEL_NOT_FOUND);
        }
        return response.items().get(0);
    }

    private Long extractSubscriberCount(YouTubeChannelListResponse.Item channel) {
        YouTubeChannelListResponse.Statistics stats = channel.statistics();
        if (stats == null || stats.subscriberCount() == null) {
            return null;
        }
        try {
            return Long.parseLong(stats.subscriberCount());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
