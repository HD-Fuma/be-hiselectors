package com.fuma.hiselectors.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.oauth.instagram.config.InstagramOAuthProperties;
import com.fuma.hiselectors.oauth.instagram.service.InstagramOAuthService;
import com.fuma.hiselectors.oauth.youtube.config.YouTubeOAuthProperties;
import com.fuma.hiselectors.oauth.youtube.service.YouTubeOAuthService;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OAuthContentCountServiceTest {

    @Test
    void instagramVerifyReturnsMediaCount() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OAuthStateProvider stateProvider = stateProvider();
        when(stateProvider.createVerificationToken(
                "hi-user", SnsPlatform.INSTAGRAM, "creator.handle", 12_345L, 120L))
                .thenReturn("instagram-verification-token");
        InstagramOAuthService service = new InstagramOAuthService(
                new InstagramOAuthProperties("client", "secret", "https://redirect", "scope"),
                stateProvider,
                builder.build());
        server.expect(request -> assertThat(request.getURI().getPath())
                        .isEqualTo("/oauth/access_token"))
                .andRespond(withSuccess(
                        "{\"access_token\":\"token\"}", MediaType.APPLICATION_JSON));
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/me");
                    assertThat(decodedQuery(request.getURI().getRawQuery()))
                            .contains("fields=id,username,followers_count,media_count");
                })
                .andRespond(withSuccess("""
                        {
                          "id": "17841400000000000",
                          "username": "creator.handle",
                          "followers_count": 12345,
                          "media_count": 120
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = service.verifyAccountOwnership("code", "state", "hi-user");

        assertThat(response.followerCount()).isEqualTo(12_345L);
        assertThat(response.contentCount()).isEqualTo(120L);
        assertThat(response.verificationToken()).isEqualTo("instagram-verification-token");
        server.verify();
    }

    @Test
    void youtubeVerifyReturnsVideoCount() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OAuthStateProvider stateProvider = stateProvider();
        when(stateProvider.createVerificationToken(
                "hi-user", SnsPlatform.YOUTUBE, "UC123", 12_345L, 120L))
                .thenReturn("youtube-verification-token");
        YouTubeOAuthService service = new YouTubeOAuthService(
                new YouTubeOAuthProperties("client", "secret", "https://redirect", "scope"),
                stateProvider,
                builder.build());
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/token"))
                .andRespond(withSuccess(
                        "{\"access_token\":\"token\"}", MediaType.APPLICATION_JSON));
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/youtube/v3/channels");
                    assertThat(decodedQuery(request.getURI().getRawQuery()))
                            .contains("part=snippet,statistics")
                            .contains("mine=true");
                })
                .andRespond(withSuccess("""
                        {
                          "items": [{
                            "id": "UC123",
                            "snippet": {"title": "creator channel"},
                            "statistics": {
                              "subscriberCount": "12345",
                              "videoCount": "120"
                            }
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = service.verifyChannelOwnership("code", "state", "hi-user");

        assertThat(response.channels()).hasSize(1);
        var channel = response.channels().get(0);
        assertThat(channel.followerCount()).isEqualTo(12_345L);
        assertThat(channel.contentCount()).isEqualTo(120L);
        assertThat(channel.verificationToken()).isEqualTo("youtube-verification-token");
        server.verify();
    }

    private OAuthStateProvider stateProvider() {
        OAuthStateProvider stateProvider = mock(OAuthStateProvider.class);
        when(stateProvider.resolveHiId("state")).thenReturn("hi-user");
        return stateProvider;
    }

    private String decodedQuery(String query) {
        return URLDecoder.decode(query, StandardCharsets.UTF_8);
    }
}
