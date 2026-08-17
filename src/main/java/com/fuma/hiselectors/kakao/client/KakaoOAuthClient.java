package com.fuma.hiselectors.kakao.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuma.hiselectors.kakao.config.KakaoOAuthProperties;
import com.fuma.hiselectors.kakao.dto.KakaoTokenResponse;
import com.fuma.hiselectors.kakao.dto.KakaoUserResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class KakaoOAuthClient {

    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String USER_ME_URI = "https://kapi.kakao.com/v2/user/me";

    private final RestClient restClient;
    private final KakaoOAuthProperties properties;
    private final ObjectMapper objectMapper;

    public KakaoOAuthClient(RestClient oauthRestClient,
                            KakaoOAuthProperties properties) {
        this.restClient = oauthRestClient;
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
    }

    public KakaoTokenResponse exchangeCode(String code) {
        MultiValueMap<String, String> form = baseTokenForm("authorization_code");
        form.add("redirect_uri", properties.redirectUri());
        form.add("code", code);
        return requestToken(form);
    }

    public KakaoTokenResponse refreshToken(String refreshToken) {
        MultiValueMap<String, String> form = baseTokenForm("refresh_token");
        form.add("refresh_token", refreshToken);
        return requestToken(form);
    }

    public KakaoUserResponse getUser(String accessToken) {
        try {
            KakaoUserResponse response = restClient.get()
                    .uri(USER_ME_URI)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(KakaoUserResponse.class);
            if (response == null || response.id() == null) {
                throw new KakaoApiException(502, null, "Empty Kakao user response");
            }
            return response;
        } catch (RestClientException e) {
            throw toApiException(e);
        }
    }

    private MultiValueMap<String, String> baseTokenForm(String grantType) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", grantType);
        form.add("client_id", properties.restApiKey());
        if (properties.clientSecret() != null && !properties.clientSecret().isBlank()) {
            form.add("client_secret", properties.clientSecret());
        }
        return form;
    }

    private KakaoTokenResponse requestToken(MultiValueMap<String, String> form) {
        try {
            KakaoTokenResponse response = restClient.post()
                    .uri(TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(KakaoTokenResponse.class);
            if (response == null || response.accessToken() == null) {
                throw new KakaoApiException(502, null, "Empty Kakao token response");
            }
            return response;
        } catch (RestClientException e) {
            throw toApiException(e);
        }
    }

    public KakaoApiException toApiException(RestClientException e) {
        if (!(e instanceof RestClientResponseException responseException)) {
            return new KakaoApiException(503, null, "Kakao API request failed");
        }

        Integer code = null;
        String message = "Kakao API request failed";
        try {
            JsonNode json = objectMapper.readTree(responseException.getResponseBodyAsString());
            if (json.has("code")) {
                code = json.get("code").asInt();
            }
            if (json.has("msg")) {
                message = json.get("msg").asText();
            } else if (json.has("error_description")) {
                message = json.get("error_description").asText();
            }
        } catch (Exception ignored) {
            // 민감한 원문 응답을 로그로 남기지 않는다.
        }
        return new KakaoApiException(responseException.getStatusCode().value(), code, message);
    }
}
