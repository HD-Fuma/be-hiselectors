package com.fuma.hiselectors.kakao.client;

import com.fuma.hiselectors.kakao.dto.KakaoFriendResponse;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class KakaoFriendClient {

    private static final String FRIENDS_URI = "https://kapi.kakao.com/v1/api/talk/friends";

    private final RestClient restClient;
    private final KakaoOAuthClient oauthClient;

    public KakaoFriendClient(RestClient oauthRestClient, KakaoOAuthClient oauthClient) {
        this.restClient = oauthRestClient;
        this.oauthClient = oauthClient;
    }

    public KakaoFriendResponse getFriends(String accessToken, int offset, int limit) {
        try {
            KakaoFriendResponse response = restClient.get()
                    .uri(UriComponentsBuilder.fromUriString(FRIENDS_URI)
                            .queryParam("offset", offset)
                            .queryParam("limit", limit)
                            .queryParam("order", "asc")
                            .queryParam("friend_order", "nickname")
                            .build(true).toUri())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(KakaoFriendResponse.class);
            return response == null
                    ? new KakaoFriendResponse(List.of(), 0)
                    : new KakaoFriendResponse(
                            response.elements() == null ? List.of() : response.elements(),
                            response.totalCount());
        } catch (RestClientResponseException e) {
            throw oauthClient.toApiException(e);
        }
    }
}
