package com.fuma.hiselectors.creator.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fuma.hiselectors.creator.discovery.dto.InstagramBusinessDiscoveryResponse.BusinessDiscovery;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MetaGraphApiClientTest {

    @Test
    void businessDiscovery_필드를_한번만_인코딩해_요청한다() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        MetaGraphProperties properties = new MetaGraphProperties(
                "https://graph.facebook.com", "v26.0", "test-token", "ig-user-id"
        );
        MetaGraphApiClient client = new MetaGraphApiClient(
                properties, restClientBuilder.build()
        );
        String response = """
                {
                  "business_discovery": {
                    "id": "17841473949573274",
                    "username": "imdayeda",
                    "followers_count": 115,
                    "media_count": 32,
                    "media": { "data": [] }
                  },
                  "id": "ig-user-id"
                }
                """;

        server.expect(request -> {
                    assertThat(request.getURI().getPath())
                            .isEqualTo("/v26.0/ig-user-id");
                    assertThat(request.getURI().getRawQuery())
                            .contains("fields=business_discovery.username(imdayeda)%7B")
                            .contains("media.limit(5)%7B")
                            .doesNotContain("%257B", "%257D", "access_token", "test-token");
                })
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        BusinessDiscovery result = client.discover("@imdayeda");

        assertThat(result.username()).isEqualTo("imdayeda");
        assertThat(result.followersCount()).isEqualTo(115L);
        assertThat(result.mediaCount()).isEqualTo(32L);
        server.verify();
    }

    @Test
    void 프로_계정이_아닌_대상은_조회_불가로_분류한다() {
        assertErrorMappedTo("""
                {"error":{"message":"Target is not an Instagram business account",
                "code":110,"error_subcode":2207013}}
                """, ErrorCode.INSTAGRAM_DISCOVERY_ACCOUNT_NOT_FOUND);
    }

    @Test
    void 권한_오류_code_10은_계정_없음으로_오인하지_않는다() {
        assertErrorMappedTo("""
                {"error":{"message":"Application does not have permission",
                "code":10}}
                """, ErrorCode.META_GRAPH_API_CALL_FAILED);
    }

    @Test
    void 일반적인_잘못된_요청은_API_호출_실패로_분류한다() {
        assertErrorMappedTo("""
                {"error":{"message":"Invalid parameter","code":100}}
                """, ErrorCode.META_GRAPH_API_CALL_FAILED);
    }

    private void assertErrorMappedTo(String responseBody, ErrorCode expectedErrorCode) {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        MetaGraphApiClient client = new MetaGraphApiClient(
                new MetaGraphProperties(
                        "https://graph.facebook.com", "v26.0", "test-token", "ig-user-id"),
                restClientBuilder.build()
        );
        server.expect(request -> { })
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(responseBody));

        assertThatThrownBy(() -> client.discover("imdayeda"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(expectedErrorCode);
        server.verify();
    }
}
