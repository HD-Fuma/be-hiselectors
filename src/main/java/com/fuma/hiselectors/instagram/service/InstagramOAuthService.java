package com.fuma.hiselectors.instagram.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.instagram.config.InstagramOAuthProperties;
import com.fuma.hiselectors.instagram.dto.InstagramProfileResponse;
import com.fuma.hiselectors.instagram.dto.InstagramTokenResponse;
import com.fuma.hiselectors.instagram.dto.InstagramVerifyResponse;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class InstagramOAuthService {

    private static final String INSTAGRAM_AUTH_URI = "https://www.instagram.com/oauth/authorize";
    private static final String INSTAGRAM_TOKEN_URI = "https://api.instagram.com/oauth/access_token";
    private static final String INSTAGRAM_ME_URI = "https://graph.instagram.com/me";

    private final InstagramOAuthProperties properties;
    private final InstagramOAuthStateProvider stateProvider;
    private final RestClient restClient;

    public InstagramOAuthService(InstagramOAuthProperties properties,
                                 InstagramOAuthStateProvider stateProvider) {
        this.properties = properties;
        this.stateProvider = stateProvider;
        // 외부(Instagram/Meta) API 지연으로 톰캣 스레드가 무한 대기하는 것을 막기 위해 타임아웃 지정
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public String buildAuthorizationUrl(String hiId) {
        String state = stateProvider.create(hiId);
        return UriComponentsBuilder.fromUriString(INSTAGRAM_AUTH_URI)
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", properties.scope())
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    public InstagramVerifyResponse verifyAccountOwnership(String code, String state, String requesterHiId) {
        String stateHiId = resolveHiId(state);
        if (!stateHiId.equals(requesterHiId)) {
            throw new BusinessException(ErrorCode.INSTAGRAM_STATE_INVALID);
        }

        String accessToken = exchangeCodeForAccessToken(code);
        InstagramProfileResponse profile = fetchMyProfile(accessToken);
        return InstagramVerifyResponse.of(profile.id(), profile.username(), profile.followersCount());
    }

    private String resolveHiId(String state) {
        try {
            return stateProvider.resolveHiId(state);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INSTAGRAM_STATE_INVALID);
        }
    }

    private String exchangeCodeForAccessToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", properties.redirectUri());
        form.add("code", code);

        InstagramTokenResponse token;
        try {
            token = restClient.post()
                    .uri(INSTAGRAM_TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(InstagramTokenResponse.class);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.INSTAGRAM_OAUTH_FAILED);
        }

        if (token == null || token.accessToken() == null) {
            throw new BusinessException(ErrorCode.INSTAGRAM_OAUTH_FAILED);
        }
        return token.accessToken();
    }

    private InstagramProfileResponse fetchMyProfile(String accessToken) {
        InstagramProfileResponse profile;
        try {
            profile = restClient.get()
                    .uri(UriComponentsBuilder.fromUriString(INSTAGRAM_ME_URI)
                            .queryParam("fields", "id,username,followers_count")
                            .queryParam("access_token", accessToken)
                            .encode()
                            .build()
                            .toUri())
                    .retrieve()
                    .body(InstagramProfileResponse.class);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.INSTAGRAM_OAUTH_FAILED);
        }

        if (profile == null || profile.id() == null) {
            throw new BusinessException(ErrorCode.INSTAGRAM_ACCOUNT_NOT_FOUND);
        }
        return profile;
    }
}
