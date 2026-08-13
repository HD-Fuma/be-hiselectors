package com.fuma.hiselectors.creator.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fuma.hiselectors.creator.discovery.dto.InstagramBusinessDiscoveryResponse.BusinessDiscovery;
import org.junit.jupiter.api.Test;
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
                            .doesNotContain("%257B", "%257D");
                })
                .andExpect(queryParam("access_token", "test-token"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        BusinessDiscovery result = client.discover("@imdayeda");

        assertThat(result.username()).isEqualTo("imdayeda");
        assertThat(result.followersCount()).isEqualTo(115L);
        assertThat(result.mediaCount()).isEqualTo(32L);
        server.verify();
    }
}
