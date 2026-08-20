package com.fuma.hiselectors.content.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.client.ContentFetcher.FetchStatus;
import com.fuma.hiselectors.content.client.dto.RawContent;
import com.fuma.hiselectors.content.client.dto.RawContentMedia;
import com.fuma.hiselectors.content.config.InstagramCollectionProperties;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class InstagramContentFetcherTest {

    private static final String BUSINESS_ACCOUNT_ID = "test-business-account-id";
    private static final String ACCESS_TOKEN = "test-long-lived-token";

    private MockRestServiceServer server;
    private InstagramContentFetcher client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new InstagramContentFetcher(
                new InstagramCollectionProperties("v24.0", BUSINESS_ACCOUNT_ID, ACCESS_TOKEN),
                builder.build());
    }

    @Test
    @DisplayName("Business Discovery로 현재 기수 Instagram 콘텐츠를 모두 조회한다")
    void collectGenerationMedia() {
        server.expect(request -> {
                    assertThat(request.getURI().getPath())
                            .isEqualTo("/v24.0/" + BUSINESS_ACCOUNT_ID);
                    String query = URLDecoder.decode(
                            request.getURI().getRawQuery(), StandardCharsets.UTF_8);
                    assertThat(query)
                            .contains("business_discovery.username(nike)")
                            .contains("media.limit(25)")
                            .contains("media_product_type")
                            .contains("children{id,media_type,media_url}");
                    assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                            .isEqualTo("Bearer " + ACCESS_TOKEN);
                })
                .andRespond(withSuccess("""
                        {
                          "business_discovery": {
                            "media": {
                              "data": [
                                {
                                  "id": "reel-new",
                                  "caption": "new reel caption",
                                  "media_type": "VIDEO",
                                  "media_product_type": "REELS",
                                  "media_url": "https://cdn.example.com/reel-new.mp4",
                                  "permalink": "https://www.instagram.com/reel/new",
                                  "timestamp": "2026-08-13T05:00:00+0000"
                                },
                                {
                                  "id": "feed-old",
                                  "caption": "existing feed caption",
                                  "media_type": "IMAGE",
                                  "media_product_type": "FEED",
                                  "media_url": "https://cdn.example.com/feed-old.jpg",
                                  "permalink": "https://www.instagram.com/p/old",
                                  "timestamp": "2026-08-13T03:00:00+0000"
                                },
                                {
                                  "id": "video-new",
                                  "caption": "new feed video caption",
                                  "media_type": "VIDEO",
                                  "media_product_type": "FEED",
                                  "permalink": "https://www.instagram.com/p/video",
                                  "timestamp": "2026-08-13T06:00:00+0000"
                                }
                              ]
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        List<RawContent> result = fetchByAccount(
                "nike", LocalDateTime.of(2026, 8, 13, 12, 0));

        assertThat(result).hasSize(3);
        assertThat(result).extracting(RawContent::snsContentId)
                .containsExactly("reel-new", "feed-old", "video-new");
        assertThat(result.getFirst().snsCode()).isEqualTo(SnsPlatform.INSTAGRAM);
        assertThat(result.getFirst().contentUrl())
                .isEqualTo("https://www.instagram.com/reel/new");
        assertThat(result.getFirst().contentType()).isEqualTo(ContentType.SHORT_FORM);
        assertThat(result.getFirst().texts()).containsExactly("new reel caption");
        assertThat(result.getFirst().caption()).isEqualTo("new reel caption");
        assertThat(result.getFirst().createdAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 13, 14, 0));
        assertThat(result.getFirst().media()).containsExactly(new RawContentMedia(
                "reel-new",
                RawContentMedia.MediaType.VIDEO,
                "https://cdn.example.com/reel-new.mp4"));
        assertThat(result.get(2).contentType()).isEqualTo(ContentType.FEED);
        assertThat(result.get(2).contentUrl())
                .isEqualTo("https://www.instagram.com/p/video");
        assertThat(result.get(2).media()).containsExactly(new RawContentMedia(
                "video-new",
                RawContentMedia.MediaType.VIDEO,
                null));
        server.verify();
    }

    @Test
    @DisplayName("캐러셀 게시물의 여러 미디어를 RawContent에 담는다")
    void collectCarouselMedia() {
        server.expect(request -> assertThat(URLDecoder.decode(
                        request.getURI().getRawQuery(), StandardCharsets.UTF_8))
                        .contains("business_discovery.username(pharrell)")
                        .contains("children{id,media_type,media_url}"))
                .andRespond(withSuccess("""
                        {
                          "business_discovery": {
                            "media": {
                              "data": [{
                                "id": "carousel-post",
                                "caption": "carousel caption",
                                "media_type": "CAROUSEL_ALBUM",
                                "media_product_type": "FEED",
                                "permalink": "https://www.instagram.com/p/carousel",
                                "timestamp": "2026-08-13T05:00:00+0000",
                                "children": {
                                  "data": [
                                    {
                                      "id": "carousel-image",
                                      "media_type": "IMAGE",
                                      "media_url": "https://cdn.example.com/image.jpg"
                                    },
                                    {
                                      "id": "carousel-video",
                                      "media_type": "VIDEO",
                                      "media_url": "https://cdn.example.com/video.mp4"
                                    }
                                  ]
                                }
                              }]
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        List<RawContent> result = fetchByAccount(
                "pharrell", LocalDateTime.of(2026, 8, 13, 13, 0));

        assertThat(result).singleElement().satisfies(content -> {
            assertThat(content.contentUrl())
                    .isEqualTo("https://www.instagram.com/p/carousel");
            assertThat(content.contentType()).isEqualTo(ContentType.FEED);
            assertThat(content.caption()).isEqualTo("carousel caption");
            assertThat(content.createdAt()).isEqualTo(LocalDateTime.of(2026, 8, 13, 14, 0));
            assertThat(content.media()).containsExactly(
                    new RawContentMedia(
                            "carousel-image",
                            RawContentMedia.MediaType.IMAGE,
                            "https://cdn.example.com/image.jpg"),
                    new RawContentMedia(
                            "carousel-video",
                            RawContentMedia.MediaType.VIDEO,
                            "https://cdn.example.com/video.mp4"));
        });
        server.verify();
    }

    @Test
    @DisplayName("Business Discovery의 다음 페이지를 이어서 조회한다")
    void collectNextPage() {
        String nextUrl = "https://graph.facebook.com/v24.0/target-id/media?after=cursor";
        server.expect(request -> assertThat(request.getURI().getPath())
                        .isEqualTo("/v24.0/" + BUSINESS_ACCOUNT_ID))
                .andRespond(withSuccess("""
                        {
                          "business_discovery": {
                            "media": {
                              "data": [{
                                "id": "first",
                                "media_type": "IMAGE",
                                "media_product_type": "FEED",
                                "media_url": "https://cdn.example.com/first.jpg",
                                "permalink": "https://www.instagram.com/p/first",
                                "timestamp": "2026-08-13T05:00:00+0000"
                              }],
                              "paging": {
                                "next": "https://graph.facebook.com/v24.0/target-id/media?after=cursor"
                              }
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(request -> {
                    assertThat(request.getURI().toString()).isEqualTo(nextUrl);
                    assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                            .isEqualTo("Bearer " + ACCESS_TOKEN);
                })
                .andRespond(withSuccess("""
                        {
                          "data": [{
                            "id": "second",
                            "media_type": "IMAGE",
                            "media_product_type": "FEED",
                            "media_url": "https://cdn.example.com/second.jpg",
                            "permalink": "https://www.instagram.com/p/second",
                            "timestamp": "2026-08-13T04:30:00+0000"
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<RawContent> result = fetchByAccount(
                "pharrell", LocalDateTime.of(2026, 8, 13, 13, 0));

        assertThat(result).extracting(RawContent::snsContentId)
                .containsExactly("first", "second");
        server.verify();
    }

    @Test
    @DisplayName("기간 외 게시물이 3개 연속이면 다음 페이지를 계속 조회한다")
    void continueAfterThreeConsecutiveOutOfPeriodContents() {
        String nextUrl = nextUrl("page-2");
        expectFirstPage(List.of(
                mediaJson("old-1", "2026-08-13T03:00:00+0000"),
                mediaJson("old-2", "2026-08-13T02:59:00+0000"),
                mediaJson("old-3", "2026-08-13T02:58:00+0000")), nextUrl);
        expectNextPage(nextUrl, List.of(
                mediaJson("current", "2026-08-13T05:00:00+0000")), null);

        List<RawContent> result = fetchByAccount(
                "nike", LocalDateTime.of(2026, 8, 13, 13, 0));

        assertThat(result).extracting(RawContent::snsContentId)
                .containsExactly("current");
        server.verify();
    }

    @Test
    @DisplayName("페이지 끝에 기간 외 게시물이 4개 연속이면 다음 페이지를 조회하지 않는다")
    void stopAfterFourConsecutiveOutOfPeriodContents() {
        expectFirstPage(List.of(
                mediaJson("old-1", "2026-08-13T03:00:00+0000"),
                mediaJson("old-2", "2026-08-13T02:59:00+0000"),
                mediaJson("old-3", "2026-08-13T02:58:00+0000"),
                mediaJson("old-4", "2026-08-13T02:57:00+0000")), nextUrl("unused"));

        List<RawContent> result = fetchByAccount(
                "nike", LocalDateTime.of(2026, 8, 13, 13, 0));

        assertThat(result).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("이미 받은 페이지는 기간 외 게시물 4개 뒤까지 끝까지 확인한다")
    void scanWholeFetchedPage() {
        String nextUrl = nextUrl("page-2");
        expectFirstPage(List.of(
                mediaJson("old-1", "2026-08-13T03:00:00+0000"),
                mediaJson("old-2", "2026-08-13T02:59:00+0000"),
                mediaJson("old-3", "2026-08-13T02:58:00+0000"),
                mediaJson("old-4", "2026-08-13T02:57:00+0000"),
                mediaJson("current-1", "2026-08-13T05:00:00+0000")), nextUrl);
        expectNextPage(nextUrl, List.of(
                mediaJson("current-2", "2026-08-13T04:59:00+0000")), null);

        List<RawContent> result = fetchByAccount(
                "nike", LocalDateTime.of(2026, 8, 13, 13, 0));

        assertThat(result).extracting(RawContent::snsContentId)
                .containsExactly("current-1", "current-2");
        server.verify();
    }

    @Test
    @DisplayName("기간 외 게시물 연속 횟수를 페이지 경계에서도 유지한다")
    void carryConsecutiveCountAcrossPages() {
        String secondPageUrl = nextUrl("page-2");
        expectFirstPage(List.of(
                mediaJson("old-1", "2026-08-13T03:00:00+0000"),
                mediaJson("old-2", "2026-08-13T02:59:00+0000"),
                mediaJson("old-3", "2026-08-13T02:58:00+0000")), secondPageUrl);
        expectNextPage(secondPageUrl, List.of(
                mediaJson("old-4", "2026-08-13T02:57:00+0000")), nextUrl("unused"));

        List<RawContent> result = fetchByAccount(
                "nike", LocalDateTime.of(2026, 8, 13, 13, 0));

        assertThat(result).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("기간 내 게시물이 나오면 페이지 간 연속 횟수를 초기화한다")
    void resetConsecutiveCountAcrossPages() {
        String secondPageUrl = nextUrl("page-2");
        String thirdPageUrl = nextUrl("page-3");
        expectFirstPage(List.of(
                mediaJson("old-1", "2026-08-13T03:00:00+0000"),
                mediaJson("old-2", "2026-08-13T02:59:00+0000"),
                mediaJson("old-3", "2026-08-13T02:58:00+0000")), secondPageUrl);
        expectNextPage(secondPageUrl, List.of(
                mediaJson("old-4", "2026-08-13T02:57:00+0000"),
                mediaJson("current-1", "2026-08-13T05:00:00+0000")), thirdPageUrl);
        expectNextPage(thirdPageUrl, List.of(
                mediaJson("current-2", "2026-08-13T04:59:00+0000")), null);

        List<RawContent> result = fetchByAccount(
                "nike", LocalDateTime.of(2026, 8, 13, 13, 0));

        assertThat(result).extracting(RawContent::snsContentId)
                .containsExactly("current-1", "current-2");
        server.verify();
    }

    @Test
    @DisplayName("페이지가 10개를 넘어도 종료 조건 전까지 계속 조회한다")
    void collectMoreThanTenPages() {
        for (int page = 0; page < 11; page++) {
            String followingUrl = page == 10 ? null : nextUrl("page-" + (page + 2));
            List<String> data = List.of(mediaJson(
                    "current-" + page, "2026-08-13T05:00:00+0000"));
            if (page == 0) {
                expectFirstPage(data, followingUrl);
            } else {
                expectNextPage(nextUrl("page-" + (page + 1)), data, followingUrl);
            }
        }

        List<RawContent> result = fetchByAccount(
                "nike", LocalDateTime.of(2026, 8, 13, 13, 0));

        assertThat(result).hasSize(11);
        server.verify();
    }

    @Test
    @DisplayName("같은 다음 페이지 URL이 반복되면 수집을 실패시킨다")
    void rejectRepeatedNextUrl() {
        String repeatedUrl = nextUrl("repeated");
        expectFirstPage(List.of(
                mediaJson("current-1", "2026-08-13T05:00:00+0000")), repeatedUrl);
        expectNextPage(repeatedUrl, List.of(
                mediaJson("current-2", "2026-08-13T04:59:00+0000")), repeatedUrl);

        assertThatThrownBy(() -> fetchByAccount(
                "nike", LocalDateTime.of(2026, 8, 13, 13, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INSTAGRAM_API_CALL_FAILED);
        server.verify();
    }

    @Test
    @DisplayName("캐러셀 미디어가 누락되면 조회 실패로 처리한다")
    void rejectCarouselWithoutMedia() {
        server.expect(request -> assertThat(request.getURI().getPath())
                        .isEqualTo("/v24.0/" + BUSINESS_ACCOUNT_ID))
                .andRespond(withSuccess("""
                        {
                          "business_discovery": {
                            "media": {
                              "data": [{
                                "id": "carousel-without-media",
                                "media_type": "CAROUSEL_ALBUM",
                                "media_product_type": "FEED",
                                "permalink": "https://www.instagram.com/p/carousel",
                                "timestamp": "2026-08-13T05:00:00+0000"
                              }]
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fetchByAccount(
                "nike", LocalDateTime.of(2026, 8, 13, 13, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INSTAGRAM_API_CALL_FAILED);
        server.verify();
    }

    @Test
    @DisplayName("Instagram 수집 설정이 없으면 API를 호출하지 않는다")
    void rejectMissingConfiguration() {
        InstagramContentFetcher unconfiguredClient = new InstagramContentFetcher(
                new InstagramCollectionProperties("v24.0", "", ""),
                RestClient.create());

        assertThatThrownBy(() -> unconfiguredClient.fetchByAccount(
                "nike", LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INSTAGRAM_COLLECTION_CONFIG_MISSING);
    }

    @Test
    @DisplayName("올바르지 않은 Instagram username을 거부한다")
    void rejectInvalidUsername() {
        assertThatThrownBy(() -> fetchByAccount(
                "invalid)username", LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("Meta Graph API 오류를 비즈니스 예외로 변환한다")
    void convertApiFailure() {
        server.expect(request -> assertThat(request.getURI().getHost())
                        .isEqualTo("graph.facebook.com"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error":{"message":"permission denied","code":100}}
                                """));

        assertThatThrownBy(() -> fetchByAccount(
                "pharrell", LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INSTAGRAM_API_CALL_FAILED);
        server.verify();
    }

    @Test
    @DisplayName("Instagram 게시물 ID별로 최신 내용과 성과를 조회한다")
    void fetchContentsByIds() {
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/v24.0/media-found");
                    assertThat(URLDecoder.decode(
                            request.getURI().getRawQuery(), StandardCharsets.UTF_8))
                            .contains("fields=id,caption,media_type,media_product_type,permalink,"
                                    + "timestamp,media_url,children{id,media_type,media_url},"
                                    + "like_count,comments_count");
                    assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                            .isEqualTo("Bearer " + ACCESS_TOKEN);
                })
                .andRespond(withSuccess("""
                        {
                          "id": "media-found",
                          "caption": "updated caption",
                          "media_type": "IMAGE",
                          "media_product_type": "FEED",
                          "media_url": "https://cdn.example.com/media-found.jpg",
                          "permalink": "https://www.instagram.com/p/media-found",
                          "timestamp": "2026-08-13T05:00:00+0000",
                          "like_count": 20,
                          "comments_count": 3
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(request -> assertThat(request.getURI().getPath())
                        .isEqualTo("/v24.0/media-missing"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        List<ContentFetcher.FetchResult> result =
                client.fetchByContentIds(List.of("media-found", "media-missing"));

        assertThat(result).hasSize(2);
        assertThat(result.getFirst()).satisfies(found -> {
            assertThat(found.snsContentId()).isEqualTo("media-found");
            assertThat(found.status()).isEqualTo(FetchStatus.FOUND);
            assertThat(found.content().caption()).isEqualTo("updated caption");
            assertThat(found.engagement().viewCount()).isNull();
            assertThat(found.engagement().likeCount()).isEqualTo(20L);
            assertThat(found.engagement().commentCount()).isEqualTo(3L);
            assertThat(found.engagement().shareCount()).isNull();
        });
        assertThat(result.get(1)).satisfies(missing -> {
            assertThat(missing.snsContentId()).isEqualTo("media-missing");
            assertThat(missing.status()).isEqualTo(FetchStatus.NOT_FOUND);
            assertThat(missing.content()).isNull();
            assertThat(missing.engagement()).isNull();
        });
        server.verify();
    }

    @Test
    @DisplayName("Instagram Graph 오류 코드 100과 하위 코드 33이면 없는 게시물로 처리한다")
    void treatObjectNotFoundGraphErrorAsNotFound() {
        expectContentError("media-missing", """
                {"error":{"message":"Unsupported get request","code":100,"error_subcode":33}}
                """);

        List<ContentFetcher.FetchResult> result =
                client.fetchByContentIds(List.of("media-missing"));

        assertThat(result).singleElement()
                .extracting(ContentFetcher.FetchResult::status)
                .isEqualTo(FetchStatus.NOT_FOUND);
        server.verify();
    }

    @Test
    @DisplayName("Instagram Graph 오류 코드가 100이 아니면 하위 코드 33이어도 조회 실패로 처리한다")
    void failWhenGraphErrorCodeDoesNotMatch() {
        expectContentError("media-failed", """
                {"error":{"message":"Other error","code":190,"error_subcode":33}}
                """);

        List<ContentFetcher.FetchResult> result =
                client.fetchByContentIds(List.of("media-failed"));

        assertThat(result).singleElement()
                .extracting(ContentFetcher.FetchResult::status)
                .isEqualTo(FetchStatus.FAILED);
        server.verify();
    }

    @Test
    @DisplayName("Instagram Graph 하위 오류 코드가 33이 아니면 오류 코드 100이어도 조회 실패로 처리한다")
    void failWhenGraphErrorSubcodeDoesNotMatch() {
        expectContentError("media-failed", """
                {"error":{"message":"Other error","code":100,"error_subcode":34}}
                """);

        List<ContentFetcher.FetchResult> result =
                client.fetchByContentIds(List.of("media-failed"));

        assertThat(result).singleElement()
                .extracting(ContentFetcher.FetchResult::status)
                .isEqualTo(FetchStatus.FAILED);
        server.verify();
    }

    @Test
    @DisplayName("Instagram Graph 400 응답 본문이 JSON이 아니면 조회 실패로 처리한다")
    void failWhenBadRequestBodyIsNotJson() {
        expectContentError("media-failed", "not-json");

        List<ContentFetcher.FetchResult> result =
                client.fetchByContentIds(List.of("media-failed"));

        assertThat(result).singleElement()
                .extracting(ContentFetcher.FetchResult::status)
                .isEqualTo(FetchStatus.FAILED);
        server.verify();
    }

    @Test
    @DisplayName("Instagram 게시물 하나의 조회 실패가 다른 게시물 조회를 막지 않는다")
    void continueAfterContentFailure() {
        server.expect(request -> assertThat(request.getURI().getPath())
                        .isEqualTo("/v24.0/media-failed"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(request -> assertThat(request.getURI().getPath())
                        .isEqualTo("/v24.0/media-found"))
                .andRespond(withSuccess(mediaJson(
                        "media-found", "2026-08-13T05:00:00+0000"),
                        MediaType.APPLICATION_JSON));

        List<ContentFetcher.FetchResult> result =
                client.fetchByContentIds(List.of("media-failed", "media-found"));

        assertThat(result).extracting(ContentFetcher.FetchResult::status)
                .containsExactly(FetchStatus.FAILED, FetchStatus.FOUND);
        server.verify();
    }

    private void expectFirstPage(List<String> media, String nextUrl) {
        server.expect(request -> assertThat(request.getURI().getPath())
                        .isEqualTo("/v24.0/" + BUSINESS_ACCOUNT_ID))
                .andRespond(withSuccess(firstPageJson(media, nextUrl),
                        MediaType.APPLICATION_JSON));
    }

    private void expectContentError(String snsContentId, String body) {
        server.expect(request -> assertThat(request.getURI().getPath())
                        .isEqualTo("/v24.0/" + snsContentId))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body));
    }

    private List<RawContent> fetchByAccount(
            String accountId, LocalDateTime since) {
        return client.fetchByAccount(accountId, since);
    }

    private void expectNextPage(String requestedUrl, List<String> media, String nextUrl) {
        server.expect(request -> assertThat(request.getURI().toString())
                        .isEqualTo(requestedUrl))
                .andRespond(withSuccess(nextPageJson(media, nextUrl),
                        MediaType.APPLICATION_JSON));
    }

    private String firstPageJson(List<String> media, String nextUrl) {
        return """
                {
                  "business_discovery": {
                    "media": {
                      "data": [%s]%s
                    }
                  }
                }
                """.formatted(String.join(",", media), pagingJson(nextUrl));
    }

    private String nextPageJson(List<String> media, String nextUrl) {
        return """
                {
                  "data": [%s]%s
                }
                """.formatted(String.join(",", media), pagingJson(nextUrl));
    }

    private String pagingJson(String nextUrl) {
        return nextUrl == null
                ? ""
                : ",\"paging\":{\"next\":\"" + nextUrl + "\"}";
    }

    private String mediaJson(String id, String timestamp) {
        return """
                {
                  "id": "%s",
                  "media_type": "IMAGE",
                  "media_product_type": "FEED",
                  "media_url": "https://cdn.example.com/%s.jpg",
                  "permalink": "https://www.instagram.com/p/%s",
                  "timestamp": "%s"
                }
                """.formatted(id, id, id, timestamp);
    }

    private String nextUrl(String cursor) {
        return "https://graph.facebook.com/v24.0/target-id/media?after=" + cursor;
    }
}
