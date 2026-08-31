package com.fuma.hiselectors.oauth.youtube.service;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.oauth.OAuthStateProvider;
import com.fuma.hiselectors.oauth.youtube.config.YouTubeOAuthProperties;
import com.fuma.hiselectors.oauth.youtube.dto.GoogleTokenResponse;
import com.fuma.hiselectors.oauth.youtube.dto.YouTubeChannelListResponse;
import com.fuma.hiselectors.oauth.youtube.dto.YouTubeVerifyResponse;
import java.util.List;
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
    private final OAuthStateProvider stateProvider;
    private final RestClient restClient;

    public YouTubeOAuthService(YouTubeOAuthProperties properties,
                               OAuthStateProvider stateProvider,
                               RestClient oauthRestClient) {
        this.properties = properties;
        this.stateProvider = stateProvider;
        this.restClient = oauthRestClient;
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
        // 구글 계정이 소유한 채널을 모두 반환한다. 하나만 자동 선택하지 않고 사용자가 고르게 한다.
        return YouTubeVerifyResponse.of(fetchMyChannels(accessToken).stream()
                .map(channel -> toChannel(requesterHiId, channel))
                .toList());
    }

    private YouTubeVerifyResponse.Channel toChannel(
            String requesterHiId, YouTubeChannelListResponse.Item channel) {
        String title = channel.snippet() != null ? channel.snippet().title() : null;
        Long followerCount = extractSubscriberCount(channel);
        Long contentCount = channel.statistics() == null
                ? null : parseLong(channel.statistics().videoCount());
        String verificationToken = stateProvider.createVerificationToken(
                requesterHiId,
                SnsPlatform.YOUTUBE,
                channel.id(),
                followerCount,
                contentCount);
        return new YouTubeVerifyResponse.Channel(
                channel.id(), title, followerCount, contentCount, verificationToken);
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

    private List<YouTubeChannelListResponse.Item> fetchMyChannels(String accessToken) {
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

        List<YouTubeChannelListResponse.Item> channels = response == null || response.items() == null
                ? List.of()
                : response.items().stream()
                        .filter(item -> item.id() != null && !item.id().isBlank())
                        .toList();
        if (channels.isEmpty()) {
            throw new BusinessException(ErrorCode.YOUTUBE_CHANNEL_NOT_FOUND);
        }
        return channels;
    }

    private Long extractSubscriberCount(YouTubeChannelListResponse.Item channel) {
        YouTubeChannelListResponse.Statistics stats = channel.statistics();
        if (stats == null || stats.subscriberCount() == null) {
            return null;
        }
        return parseLong(stats.subscriberCount());
    }

    private Long parseLong(String value) {
        try {
            return value == null ? null : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
