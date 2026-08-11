package com.fuma.hiselectors.youtube.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.user.model.User;
import com.fuma.hiselectors.user.repository.UserRepository;
import com.fuma.hiselectors.youtube.config.YouTubeOAuthProperties;
import com.fuma.hiselectors.youtube.dto.GoogleTokenResponse;
import com.fuma.hiselectors.youtube.dto.GoogleUserInfoResponse;
import com.fuma.hiselectors.youtube.dto.YouTubeChannelListResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class YouTubeOAuthService {

    private static final String GOOGLE_AUTH_URI = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USERINFO_URI = "https://www.googleapis.com/oauth2/v3/userinfo";
    private static final String YOUTUBE_CHANNELS_URI =
            "https://www.googleapis.com/youtube/v3/channels";

    private final YouTubeOAuthProperties properties;
    private final YouTubeOAuthStateProvider stateProvider;
    private final UserRepository userRepository;
    private final RestClient restClient;

    public YouTubeOAuthService(YouTubeOAuthProperties properties,
                               YouTubeOAuthStateProvider stateProvider,
                               UserRepository userRepository) {
        this.properties = properties;
        this.stateProvider = stateProvider;
        this.userRepository = userRepository;
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


    public String verifyChannelOwnership(String code, String state) {
        String hiId = resolveHiId(state);

        User user = userRepository.findByHiId(hiId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String accessToken = exchangeCodeForAccessToken(code);

        // 1) 인증한 구글 계정 이메일이 회원 이메일과 같은지 대조
        String googleEmail = fetchVerifiedGoogleEmail(accessToken);
        if (!emailMatches(user.getEmail(), googleEmail)) {
            throw new BusinessException(ErrorCode.YOUTUBE_EMAIL_MISMATCH);
        }

        // 2) 본인 유튜브 채널 존재 확인 (없으면 예외)
        YouTubeChannelListResponse.Item channel = fetchMyChannel(accessToken);
        return channel.snippet() != null ? channel.snippet().title() : null;
    }

    private boolean emailMatches(String userEmail, String googleEmail) {
        return StringUtils.hasText(userEmail)
                && StringUtils.hasText(googleEmail)
                && userEmail.trim().equalsIgnoreCase(googleEmail.trim());
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

    private String fetchVerifiedGoogleEmail(String accessToken) {
        GoogleUserInfoResponse userInfo;
        try {
            userInfo = restClient.get()
                    .uri(GOOGLE_USERINFO_URI)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(GoogleUserInfoResponse.class);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.YOUTUBE_OAUTH_FAILED);
        }

        if (userInfo == null || !StringUtils.hasText(userInfo.email())
                || !Boolean.TRUE.equals(userInfo.emailVerified())) {
            throw new BusinessException(ErrorCode.YOUTUBE_OAUTH_FAILED);
        }
        return userInfo.email();
    }

    private YouTubeChannelListResponse.Item fetchMyChannel(String accessToken) {
        YouTubeChannelListResponse response;
        try {
            response = restClient.get()
                    .uri(UriComponentsBuilder.fromUriString(YOUTUBE_CHANNELS_URI)
                            .queryParam("part", "snippet")
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
}
