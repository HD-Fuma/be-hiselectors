package com.fuma.hiselectors.content.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.client.ContentFetcher.FetchStatus;
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
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class YoutubeContentFetcherTest {

    private static final String API_KEY = "test-api-key";
    private static final String CHANNEL_ID = "UC0000000000000000000000";

    private MockRestServiceServer server;
    private YoutubeContentFetcher client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new YoutubeContentFetcher(
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
                                "description": "new video description",
                                "thumbnails": {
                                  "default": {"url": "https://i.ytimg.com/video-new/default.jpg"},
                                  "high": {"url": "https://i.ytimg.com/video-new/high.jpg"}
                                }
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
        expectStatistics("video-new", "video-old");

        List<RawContent> result = fetchByAccount(
                CHANNEL_ID, LocalDateTime.of(2026, 8, 13, 12, 0));

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
            assertThat(content.viewCount()).isEqualTo(100L);
            assertThat(content.likeCount()).isEqualTo(20L);
            assertThat(content.commentCount()).isEqualTo(3L);
            assertThat(content.media()).containsExactly(new RawContentMedia(
                    "video-new",
                    RawContentMedia.MediaType.VIDEO,
                    null,
                    List.of(
                            "https://i.ytimg.com/video-new/default.jpg",
                            "https://i.ytimg.com/video-new/high.jpg")));
        });
        server.verify();
    }

    @Test
    void fetchesChannelProfileImageFromSnippet() {
        expectUploadsPlaylist();

        assertThat(client.fetchProfileImageUrl(CHANNEL_ID))
                .contains("https://yt3.example.com/high.jpg");
        server.verify();
    }

    @Test
    void fetchesChannelTitlesInOneBatch() {
        String secondChannelId = "UC1111111111111111111111";
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/youtube/v3/channels");
                    assertThat(decodedQuery(request.getURI().getRawQuery()))
                            .contains("part=snippet")
                            .contains("id=" + CHANNEL_ID + "," + secondChannelId)
                            .contains("key=" + API_KEY);
                })
                .andRespond(withSuccess("""
                        {"items":[
                          {"id":"%s","snippet":{"title":"채널 하나"}},
                          {"id":"%s","snippet":{"title":"채널 둘"}}
                        ]}
                        """.formatted(CHANNEL_ID, secondChannelId), MediaType.APPLICATION_JSON));

        Map<String, String> titles = client.fetchChannelTitles(
                List.of(CHANNEL_ID, secondChannelId));

        assertThat(titles).containsExactlyInAnyOrderEntriesOf(Map.of(
                CHANNEL_ID, "채널 하나",
                secondChannelId, "채널 둘"));
        server.verify();
    }

    @Test
    void fetchesNextChannelTitleBatchAfterOneFails() {
        List<String> channelIds = IntStream.rangeClosed(0, 50)
                .mapToObj(index -> "UC%022d".formatted(index))
                .toList();
        server.expect(request -> assertThat(decodedQuery(request.getURI().getRawQuery()))
                        .contains("id=" + String.join(",", channelIds.subList(0, 50))))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(request -> assertThat(decodedQuery(request.getURI().getRawQuery()))
                        .contains("id=" + channelIds.getLast()))
                .andRespond(withSuccess("""
                        {"items":[{"id":"%s","snippet":{"title":"마지막 채널"}}]}
                        """.formatted(channelIds.getLast()), MediaType.APPLICATION_JSON));

        Map<String, String> titles = client.fetchChannelTitles(channelIds);

        assertThat(titles).containsOnly(Map.entry(channelIds.getLast(), "마지막 채널"));
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
        expectStatistics("first", "second");

        List<RawContent> result = fetchByAccount(
                CHANNEL_ID, LocalDateTime.of(2026, 8, 13, 13, 0));

        assertThat(result).extracting(RawContent::snsContentId)
                .containsExactly("first", "second");
        server.verify();
    }

    @Test
    @DisplayName("현재 기수 영상이 10페이지를 넘어도 마지막 페이지까지 조회한다")
    void collectMoreThanTenPages() {
        expectUploadsPlaylist();
        for (int pageNumber = 1; pageNumber <= 11; pageNumber++) {
            int currentPage = pageNumber;
            String expectedToken = currentPage == 1 ? null : "page-" + currentPage;
            String nextToken = currentPage == 11 ? null : "page-" + (currentPage + 1);
            server.expect(request -> {
                        String query = decodedQuery(request.getURI().getRawQuery());
                        if (expectedToken == null) {
                            assertThat(query).doesNotContain("pageToken=");
                        } else {
                            assertThat(query).contains("pageToken=" + expectedToken);
                        }
                    })
                    .andRespond(withSuccess(
                            playlistPage("video-" + currentPage, nextToken),
                            MediaType.APPLICATION_JSON));
        }
        expectStatistics(IntStream.rangeClosed(1, 11)
                .mapToObj(number -> "video-" + number)
                .toArray(String[]::new));

        List<RawContent> result = fetchByAccount(
                CHANNEL_ID, LocalDateTime.of(2026, 8, 13, 13, 0));
        assertThat(result).hasSize(11);
        assertThat(result.getLast().snsContentId()).isEqualTo("video-11");
        server.verify();
    }

    @Test
    @DisplayName("같은 YouTube 페이지 토큰이 반복되면 조회를 중단한다")
    void rejectRepeatedPageToken() {
        expectUploadsPlaylist();
        server.expect(request -> assertThat(decodedQuery(request.getURI().getRawQuery()))
                        .doesNotContain("pageToken="))
                .andRespond(withSuccess(playlistPage("first", "repeated-token"),
                        MediaType.APPLICATION_JSON));
        server.expect(request -> assertThat(decodedQuery(request.getURI().getRawQuery()))
                        .contains("pageToken=repeated-token"))
                .andRespond(withSuccess(playlistPage("second", "repeated-token"),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fetchByAccount(
                CHANNEL_ID, LocalDateTime.of(2026, 8, 13, 13, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.YOUTUBE_API_CALL_FAILED);
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

        List<RawContent> result = fetchByAccount(
                CHANNEL_ID, LocalDateTime.of(2026, 8, 13, 13, 0));

        assertThat(result).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("YouTube API 키가 없으면 API를 호출하지 않는다")
    void rejectMissingApiKey() {
        YoutubeContentFetcher unconfiguredClient = new YoutubeContentFetcher(
                new YoutubeCollectionProperties(""), RestClient.create());

        assertThatThrownBy(() -> unconfiguredClient.fetchByAccount(
                CHANNEL_ID, LocalDateTime.now()))
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

        assertThatThrownBy(() -> fetchByAccount(CHANNEL_ID, LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.YOUTUBE_CHANNEL_NOT_FOUND);
        server.verify();
    }

    @Test
    @DisplayName("YouTube 핸들로 채널의 업로드 목록을 조회한다")
    void collectByHandle() {
        expectUploadsPlaylist("forHandle=test-handle");
        expectEmptyPlaylist();

        List<RawContent> result = client.fetchByAccount(
                "@test-handle", LocalDateTime.now());

        assertThat(result).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("YouTube 채널 ID는 공개 핸들 재조회 없이 업로드 목록을 조회한다")
    void collectByChannelIdWithoutHandleLookup() {
        expectChannel("id=" + CHANNEL_ID, null, "uploads-by-id");
        expectEmptyPlaylist();

        List<RawContent> result = client.fetchByAccount(
                CHANNEL_ID, LocalDateTime.now());

        assertThat(result).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("UC로 시작해도 채널 ID 형식이 아니면 핸들로 조회한다")
    void collectHandleStartingWithUc() {
        expectUploadsPlaylist("forHandle=UCcreator");
        expectEmptyPlaylist();

        List<RawContent> result = client.fetchByAccount(
                "@UCcreator", LocalDateTime.now());

        assertThat(result).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("YouTube 채널 URL로 채널의 업로드 목록을 조회한다")
    void collectByChannelUrl() {
        expectUploadsPlaylist();
        expectEmptyPlaylist();

        List<RawContent> result = client.fetchByAccount(
                "https://www.youtube.com/channel/" + CHANNEL_ID,
                LocalDateTime.now());

        assertThat(result).isEmpty();
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

        assertThatThrownBy(() -> fetchByAccount(CHANNEL_ID, LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.YOUTUBE_API_CALL_FAILED);
        server.verify();
    }

    @Test
    @DisplayName("YouTube 영상 ID를 묶어서 최신 내용과 성과를 조회한다")
    void fetchContentsByIds() {
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/youtube/v3/videos");
                    assertThat(decodedQuery(request.getURI().getRawQuery()))
                            .contains("part=snippet,statistics")
                            .doesNotContain("status")
                            .contains("id=video-found,video-missing")
                            .contains("key=" + API_KEY);
                })
                .andRespond(withSuccess("""
                        {
                          "items": [{
                            "id": "video-found",
                            "snippet": {
                              "title": "updated title",
                              "description": "updated description",
                              "publishedAt": "2026-08-13T05:00:00Z"
                            },
                            "statistics": {
                              "viewCount": "100",
                              "likeCount": "20",
                              "commentCount": "3"
                            }
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<ContentFetcher.FetchResult> result =
                client.fetchByContentIds(List.of("video-found", "video-missing"));

        assertThat(result).hasSize(2);
        assertThat(result.getFirst()).satisfies(found -> {
            assertThat(found.snsContentId()).isEqualTo("video-found");
            assertThat(found.status()).isEqualTo(FetchStatus.FOUND);
            assertThat(found.content().caption())
                    .isEqualTo("updated title\nupdated description");
            assertThat(found.engagement().viewCount()).isEqualTo(100L);
            assertThat(found.engagement().likeCount()).isEqualTo(20L);
            assertThat(found.engagement().commentCount()).isEqualTo(3L);
            assertThat(found.engagement().shareCount()).isNull();
        });
        assertThat(result.get(1)).satisfies(missing -> {
            assertThat(missing.snsContentId()).isEqualTo("video-missing");
            assertThat(missing.status()).isEqualTo(FetchStatus.NOT_FOUND);
            assertThat(missing.content()).isNull();
            assertThat(missing.engagement()).isNull();
        });
        server.verify();
    }

    @Test
    @DisplayName("영상 길이로 SHORTS/LONG_FORM 을 판정한다")
    void classifiesShortsByDuration() {
        server.expect(request -> assertThat(decodedQuery(request.getURI().getRawQuery()))
                        .contains("part=snippet,statistics,contentDetails"))
                .andRespond(withSuccess("""
                        {
                          "items": [
                            {
                              "id": "short-1",
                              "snippet": {"title": "s", "publishedAt": "2026-08-13T05:00:00Z"},
                              "contentDetails": {"duration": "PT45S"}
                            },
                            {
                              "id": "long-1",
                              "snippet": {"title": "l", "publishedAt": "2026-08-13T05:00:00Z"},
                              "contentDetails": {"duration": "PT5M10S"}
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<ContentFetcher.FetchResult> result =
                client.fetchByContentIds(List.of("short-1", "long-1"));

        assertThat(result.get(0).content().contentType()).isEqualTo(ContentType.SHORTS);
        assertThat(result.get(1).content().contentType()).isEqualTo(ContentType.LONG_FORM);
        server.verify();
    }

    @Test
    @DisplayName("YouTube 영상 ID는 API 제한에 맞춰 50개씩 조회한다")
    void fetchContentsByIdsInBatchesOfFifty() {
        List<String> ids = java.util.stream.IntStream.rangeClosed(1, 51)
                .mapToObj(number -> "video-" + number)
                .toList();
        server.expect(request -> assertThat(decodedQuery(request.getURI().getRawQuery()))
                        .contains("id=" + String.join(",", ids.subList(0, 50))))
                .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));
        server.expect(request -> assertThat(decodedQuery(request.getURI().getRawQuery()))
                        .contains("id=video-51"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        List<ContentFetcher.FetchResult> result = client.fetchByContentIds(ids);

        assertThat(result).hasSize(51);
        assertThat(result.subList(0, 50))
                .extracting(ContentFetcher.FetchResult::status)
                .containsOnly(FetchStatus.NOT_FOUND);
        assertThat(result.getLast().status()).isEqualTo(FetchStatus.FAILED);
        server.verify();
    }

    private void expectUploadsPlaylist() {
        expectChannel("id=" + CHANNEL_ID, null, "uploads-playlist");
    }

    private void expectUploadsPlaylist(String expectedAccountQuery) {
        expectChannel(expectedAccountQuery, "@test-handle", "uploads-playlist");
    }

    private void expectChannel(
            String expectedAccountQuery, String customUrl, String uploadsPlaylistId) {
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/youtube/v3/channels");
                    String query = decodedQuery(request.getURI().getRawQuery());
                    assertThat(query)
                            .contains("part=snippet,contentDetails")
                            .contains(expectedAccountQuery)
                            .contains("key=" + API_KEY);
                })
                .andRespond(withSuccess("""
                        {
                          "items": [{
                            "id": "%s",
                            "snippet": {
                              "customUrl": "%s",
                              "thumbnails": {
                                "default": {"url": "https://yt3.example.com/default.jpg"},
                                "high": {"url": "https://yt3.example.com/high.jpg"}
                              }
                            },
                            "contentDetails": {
                              "relatedPlaylists": {
                                "uploads": "%s"
                              }
                            }
                          }]
                        }
                        """.formatted(CHANNEL_ID, customUrl, uploadsPlaylistId),
                        MediaType.APPLICATION_JSON));
    }

    private void expectEmptyPlaylist() {
        server.expect(request -> assertThat(request.getURI().getPath())
                        .isEqualTo("/youtube/v3/playlistItems"))
                .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));
    }

    private List<RawContent> fetchByAccount(
            String accountId, LocalDateTime since) {
        return client.addStatistics(client.fetchByAccount(accountId, since));
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

    private void expectStatistics(String... videoIds) {
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/youtube/v3/videos");
                    String query = decodedQuery(request.getURI().getRawQuery());
                    assertThat(query)
                            .contains("part=statistics")
                            .contains("id=" + String.join(",", videoIds))
                            .contains("key=" + API_KEY);
                })
                .andRespond(withSuccess("""
                        {"items":[%s]}
                        """.formatted(String.join(",", List.of(videoIds).stream()
                        .map(videoId -> """
                                {"id":"%s","statistics":{
                                  "viewCount":"100","likeCount":"20","commentCount":"3"
                                }}
                                """.formatted(videoId))
                        .toList())), MediaType.APPLICATION_JSON));
    }

    private String decodedQuery(String query) {
        return URLDecoder.decode(query, StandardCharsets.UTF_8);
    }
}
