package com.fuma.hiselectors.content.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.client.dto.RawContent;
import com.fuma.hiselectors.content.client.dto.RawContentMedia;
import com.fuma.hiselectors.content.config.YoutubeCollectionProperties;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class YoutubeContentClientTest {

    private static final String API_KEY = "test-api-key";
    private static final String CHANNEL_ID = "UC0000000000000000000000";

    private MockRestServiceServer server;
    private YoutubeContentClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new YoutubeContentClient(
                new YoutubeCollectionProperties(API_KEY),
                builder.build());
    }

    @Test
    @DisplayName("채널의 현재 기수 YouTube 영상을 모두 RawContent로 변환한다")
    void collectGenerationVideos() {
        expectUploadsPlaylist();
        server.expect(request -> {
                    assertThat(request.getURI().getPath())
                            .isEqualTo("/youtube/v3/playlistItems");
                    String query = decodedQuery(request.getURI().getRawQuery());
                    assertThat(query)
                            .contains("part=snippet,contentDetails")
                            .contains("playlistId=uploads-playlist")
                            .contains("maxResults=50")
                            .contains("key=" + API_KEY);
                })
                .andRespond(withSuccess("""
                        {
                          "items": [
                            {
                              "snippet": {
                                "title": "new video title",
                                "description": "new video description"
                              },
                              "contentDetails": {
                                "videoId": "video-new",
                                "videoPublishedAt": "2026-08-13T05:00:00Z"
                              }
                            },
                            {
                              "snippet": {
                                "title": "old video",
                                "description": "old description"
                              },
                              "contentDetails": {
                                "videoId": "video-old",
                                "videoPublishedAt": "2026-08-13T03:00:00Z"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        ContentPlatformClient.CollectionResult collection = client.collect(
                CHANNEL_ID, LocalDateTime.of(2026, 8, 13, 12, 0));
        List<RawContent> result = collection.contents();

        assertThat(collection.fetchedCount()).isEqualTo(2);
        assertThat(result).hasSize(2);
        assertThat(result.getFirst()).satisfies(content -> {
            assertThat(content.snsCode()).isEqualTo(SnsPlatform.YOUTUBE);
            assertThat(content.snsContentId()).isEqualTo("video-new");
            assertThat(content.contentUrl())
                    .isEqualTo("https://www.youtube.com/watch?v=video-new");
            assertThat(content.contentType()).isEqualTo(ContentType.LONG_FORM);
            assertThat(content.texts())
                    .containsExactly("new video title", "new video description");
            assertThat(content.caption())
                    .isEqualTo("new video title\nnew video description");
            assertThat(content.createdAt())
                    .isEqualTo(LocalDateTime.of(2026, 8, 13, 14, 0));
            assertThat(content.media()).containsExactly(new RawContentMedia(
                    "video-new",
                    RawContentMedia.MediaType.VIDEO,
                    null));
        });
        server.verify();
    }

    @Test
    @DisplayName("업로드 영상 목록의 다음 페이지를 이어서 조회한다")
    void collectNextPage() {
        expectUploadsPlaylist();
        server.expect(request -> assertThat(decodedQuery(request.getURI().getRawQuery()))
                        .doesNotContain("pageToken="))
                .andRespond(withSuccess(playlistPage("first", "next-page"),
                        MediaType.APPLICATION_JSON));
        server.expect(request -> assertThat(decodedQuery(request.getURI().getRawQuery()))
                        .contains("pageToken=next-page"))
                .andRespond(withSuccess(playlistPage("second", null),
                        MediaType.APPLICATION_JSON));

        List<RawContent> result = client.collect(
                CHANNEL_ID, LocalDateTime.of(2026, 8, 13, 13, 0)).contents();

        assertThat(result).extracting(RawContent::snsContentId)
                .containsExactly("first", "second");
        server.verify();
    }

    @Test
    @DisplayName("기수 시작 전 영상만 있는 페이지에서 조회를 종료한다")
    void stopAtOldPage() {
        expectUploadsPlaylist();
        server.expect(request -> assertThat(request.getURI().getPath())
                        .isEqualTo("/youtube/v3/playlistItems"))
                .andRespond(withSuccess("""
                        {
                          "nextPageToken": "next-page",
                          "items": [{
                            "snippet": {
                              "title": "old video",
                              "description": "old description"
                            },
                            "contentDetails": {
                              "videoId": "video-old",
                              "videoPublishedAt": "2026-08-13T03:00:00Z"
                            }
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<RawContent> result = client.collect(
                CHANNEL_ID, LocalDateTime.of(2026, 8, 13, 13, 0)).contents();

        assertThat(result).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("YouTube API 키가 없으면 API를 호출하지 않는다")
    void rejectMissingApiKey() {
        YoutubeContentClient unconfiguredClient = new YoutubeContentClient(
                new YoutubeCollectionProperties(""), RestClient.create());

        assertThatThrownBy(() -> unconfiguredClient.collect(CHANNEL_ID, LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.YOUTUBE_API_KEY_MISSING);
    }

    @Test
    @DisplayName("존재하지 않는 YouTube 채널을 거부한다")
    void rejectMissingChannel() {
        server.expect(request -> assertThat(request.getURI().getPath())
                        .isEqualTo("/youtube/v3/channels"))
                .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.collect(CHANNEL_ID, LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.YOUTUBE_CHANNEL_NOT_FOUND);
        server.verify();
    }

    @Test
    @DisplayName("YouTube Data API 오류를 비즈니스 예외로 변환한다")
    void convertApiFailure() {
        server.expect(request -> assertThat(request.getURI().getPath())
                        .isEqualTo("/youtube/v3/channels"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"invalid key\"}}"));

        assertThatThrownBy(() -> client.collect(CHANNEL_ID, LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.YOUTUBE_API_CALL_FAILED);
        server.verify();
    }

    private void expectUploadsPlaylist() {
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/youtube/v3/channels");
                    String query = decodedQuery(request.getURI().getRawQuery());
                    assertThat(query)
                            .contains("part=contentDetails")
                            .contains("id=" + CHANNEL_ID)
                            .contains("key=" + API_KEY);
                })
                .andRespond(withSuccess("""
                        {
                          "items": [{
                            "contentDetails": {
                              "relatedPlaylists": {
                                "uploads": "uploads-playlist"
                              }
                            }
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));
    }

    private String playlistPage(String videoId, String nextPageToken) {
        String nextPage = nextPageToken == null
                ? ""
                : "\"nextPageToken\": \"" + nextPageToken + "\",";
        return """
                {
                  %s
                  "items": [{
                    "snippet": {
                      "title": "%s title",
                      "description": "%s description"
                    },
                    "contentDetails": {
                      "videoId": "%s",
                      "videoPublishedAt": "2026-08-13T05:00:00Z"
                    }
                  }]
                }
                """.formatted(nextPage, videoId, videoId, videoId);
    }

    private String decodedQuery(String query) {
        return URLDecoder.decode(query, StandardCharsets.UTF_8);
    }
}
