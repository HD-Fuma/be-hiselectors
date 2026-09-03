package com.fuma.hiselectors.oauth.instagram.service;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.oauth.OAuthStateProvider;
import com.fuma.hiselectors.oauth.instagram.config.InstagramOAuthProperties;
import com.fuma.hiselectors.oauth.instagram.dto.InstagramProfileResponse;
import com.fuma.hiselectors.oauth.instagram.dto.InstagramTokenResponse;
import com.fuma.hiselectors.oauth.instagram.dto.InstagramVerifyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
public class InstagramOAuthService {

    private static final String INSTAGRAM_AUTH_URI = "https://www.instagram.com/oauth/authorize";
    private static final String INSTAGRAM_TOKEN_URI = "https://api.instagram.com/oauth/access_token";
    private static final String INSTAGRAM_ME_URI = "https://graph.instagram.com/me";

    private final InstagramOAuthProperties properties;
    private final OAuthStateProvider stateProvider;
    private final RestClient restClient;

    public InstagramOAuthService(InstagramOAuthProperties properties,
                                 OAuthStateProvider stateProvider,
                                 RestClient oauthRestClient) {
        this.properties = properties;
        this.stateProvider = stateProvider;
        this.restClient = oauthRestClient;
    }

    public String buildAuthorizationUrl(String hiId) {
        String state = stateProvider.create(hiId);
        return UriComponentsBuilder.fromUriString(INSTAGRAM_AUTH_URI)
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("force_reauth", true)
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
        String verificationToken = stateProvider.createVerificationToken(
                requesterHiId,
                SnsPlatform.INSTAGRAM,
                profile.username(),
                profile.followersCount(),
                profile.mediaCount());
        return InstagramVerifyResponse.of(
                profile.id(),
                profile.username(),
                profile.followersCount(),
                profile.mediaCount(),
                verificationToken);
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
            logOAuthFailure("token exchange", e);
            throw oauthFailure(e);
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
                            .queryParam("fields", "id,username,followers_count,media_count")
                            .queryParam("access_token", accessToken)
                            .encode()
                            .build()
                            .toUri())
                    .retrieve()
                    .body(InstagramProfileResponse.class);
        } catch (RuntimeException e) {
            logOAuthFailure("profile lookup", e);
            throw new BusinessException(ErrorCode.INSTAGRAM_OAUTH_FAILED);
        }

        if (profile == null || profile.id() == null
                || profile.username() == null || profile.username().isBlank()) {
            throw new BusinessException(ErrorCode.INSTAGRAM_ACCOUNT_NOT_FOUND);
        }
        return profile;
    }

    /**
     * Meta가 4xx(만료·재사용된 code 등 클라이언트 원인)를 주면 400으로, 그 외(연결 실패·5xx·타임아웃)는
     * 502로 매핑한다. 잘못된 code를 "게이트웨이 오류(502)"로 오인해 인프라를 뒤지는 헛다리를 막는다.
     */
    private BusinessException oauthFailure(RuntimeException exception) {
        if (exception instanceof RestClientResponseException responseException
                && responseException.getStatusCode().is4xxClientError()) {
            return new BusinessException(ErrorCode.INSTAGRAM_AUTH_CODE_INVALID);
        }
        return new BusinessException(ErrorCode.INSTAGRAM_OAUTH_FAILED);
    }

    private void logOAuthFailure(String stage, RuntimeException exception) {
        if (exception instanceof RestClientResponseException responseException) {
            String response = responseException.getResponseBodyAsString()
                    .replaceAll("(?i)(\"(?:access_token|client_secret)\"\\s*:\\s*\")[^\"]+", "$1[redacted]")
                    .replaceAll("[\\r\\n]+", " ");
            log.warn("Instagram OAuth {} failed: status={}, response={}",
                    stage, responseException.getStatusCode().value(),
                    response.substring(0, Math.min(response.length(), 1_000)));
            return;
        }
        log.warn("Instagram OAuth {} failed: exception={}", stage, exception.getClass().getSimpleName());
    }
}
