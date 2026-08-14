package com.fuma.hiselectors.kakao.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.kakao.dto.KakaoMessageTemplate;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KakaoMessageClient {

    private static final String SEND_ME_URI =
            "https://kapi.kakao.com/v2/api/talk/memo/default/send";
    private static final String SEND_FRIEND_URI =
            "https://kapi.kakao.com/v1/api/talk/friends/message/default/send";

    private final RestClient restClient;
    private final KakaoOAuthClient oauthClient;
    private final ObjectMapper objectMapper;

    public KakaoMessageClient(RestClient oauthRestClient, KakaoOAuthClient oauthClient) {
        this.restClient = oauthRestClient;
        this.oauthClient = oauthClient;
        this.objectMapper = new ObjectMapper();
    }

    public void sendMe(String accessToken, KakaoMessageTemplate template) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("template_object", json(template));
        try {
            MeResponse response = post(SEND_ME_URI, accessToken, form, MeResponse.class);
            if (response == null || response.resultCode() != 0) {
                throw new KakaoApiException(502, null, "Kakao send-me failed");
            }
        } catch (RestClientException e) {
            throw oauthClient.toApiException(e);
        }
    }

    public void sendFriend(String accessToken, String receiverUuid,
                           KakaoMessageTemplate template) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("receiver_uuids", json(List.of(receiverUuid)));
        form.add("template_object", json(template));
        try {
            FriendResponse response = post(SEND_FRIEND_URI, accessToken, form, FriendResponse.class);
            boolean success = response != null && response.successfulReceiverUuids() != null
                    && response.successfulReceiverUuids().contains(receiverUuid);
            if (!success) {
                Integer code = response != null && response.failureInfo() != null
                        && !response.failureInfo().isEmpty() ? response.failureInfo().getFirst().code() : null;
                throw new KakaoApiException(502, code, "Kakao friend message failed");
            }
        } catch (RestClientException e) {
            throw oauthClient.toApiException(e);
        }
    }

    private <T> T post(String uri, String token, MultiValueMap<String, String> form, Class<T> type) {
        return restClient.post().uri(uri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Authorization", "Bearer " + token)
                .body(form).retrieve().body(type);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "카카오 메시지 템플릿을 생성할 수 없습니다.");
        }
    }

    private record MeResponse(@JsonProperty("result_code") int resultCode) {
    }

    private record FriendResponse(
            @JsonProperty("successful_receiver_uuids") List<String> successfulReceiverUuids,
            @JsonProperty("failure_info") List<FailureInfo> failureInfo) {
    }

    private record FailureInfo(Integer code, String msg,
                               @JsonProperty("receiver_uuids") List<String> receiverUuids) {
    }
}
