package com.fuma.hiselectors.content.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fuma.hiselectors.application.model.SnsPlatform;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class InstagramContentClientTest {

    private static final String BUSINESS_ACCOUNT_ID = "test-business-account-id";
    private static final String ACCESS_TOKEN = "test-long-lived-token";

    private MockRestServiceServer server;
    private InstagramContentClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new InstagramContentClient(
                new InstagramCollectionProperties("v24.0", BUSINESS_ACCOUNT_ID, ACCESS_TOKEN),
                builder.build());
    }

    @Test
    @DisplayName("Business Discovery로 신규 Instagram 콘텐츠를 조회한다")
    void collectNewMedia(CapturedOutput output) {
        server.expect(request -> {
                    assertThat(request.getURI().getPath())
                            .isEqualTo("/v24.0/" + BUSINESS_ACCOUNT_ID);
                    String query = URLDecoder.decode(
                            request.getURI().getRawQuery(), StandardCharsets.UTF_8);
                    assertThat(query)
                            .contains("business_discovery.username(nike)")
                            .contains("media.limit(50)")
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

        List<RawContent> result = client.collect(
                "nike", LocalDateTime.of(2026, 8, 13, 13, 0));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(RawContent::snsContentId)
                .containsExactly("reel-new", "video-new");
        assertThat(result.getFirst().snsCode()).isEqualTo(SnsPlatform.INSTAGRAM);
        assertThat(result.getFirst().contentUrl())
                .isEqualTo("https://www.instagram.com/reel/new");
        assertThat(result.getFirst().contentType()).isEqualTo(ContentType.SHORT_FORM);
        assertThat(result.getFirst().caption()).isEqualTo("new reel caption");
        assertThat(result.getFirst().createdAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 13, 14, 0));
        assertThat(result.getFirst().media()).containsExactly(new RawContentMedia(
                "reel-new",
                RawContentMedia.MediaType.VIDEO,
                "https://cdn.example.com/reel-new.mp4"));
        assertThat(result.get(1).contentType()).isEqualTo(ContentType.FEED);
        assertThat(result.get(1).contentUrl())
                .isEqualTo("https://www.instagram.com/p/video");
        assertThat(result.get(1).media()).containsExactly(new RawContentMedia(
                "video-new",
                RawContentMedia.MediaType.VIDEO,
                null));
        assertThat(output)
                .contains("platform=INSTAGRAM")
                .contains("accountId=nike")
                .contains("collectedAfter=2026-08-13T13:00")
                .contains("fetchedCount=3")
                .contains("newCount=2")
                .contains("durationMs=");
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

        List<RawContent> result = client.collect(
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

        List<RawContent> result = client.collect(
                "pharrell", LocalDateTime.of(2026, 8, 13, 13, 0));

        assertThat(result).extracting(RawContent::snsContentId)
                .containsExactly("first", "second");
        server.verify();
    }

    @Test
    @DisplayName("신규 게시물이 없는 페이지에서 조회를 종료한다")
    void stopAtOldPage() {
        server.expect(request -> assertThat(request.getURI().getPath())
                        .isEqualTo("/v24.0/" + BUSINESS_ACCOUNT_ID))
                .andRespond(withSuccess("""
                        {
                          "business_discovery": {
                            "media": {
                              "data": [{
                                "id": "old",
                                "timestamp": "2026-08-13T03:00:00+0000"
                              }],
                              "paging": {
                                "next": "https://graph.facebook.com/v24.0/target-id/media?after=cursor"
                              }
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        List<RawContent> result = client.collect(
                "nike", LocalDateTime.of(2026, 8, 13, 13, 0));

        assertThat(result).isEmpty();
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

        assertThatThrownBy(() -> client.collect(
                "nike", LocalDateTime.of(2026, 8, 13, 13, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INSTAGRAM_API_CALL_FAILED);
        server.verify();
    }

    @Test
    @DisplayName("Instagram 수집 설정이 없으면 API를 호출하지 않는다")
    void rejectMissingConfiguration() {
        InstagramContentClient unconfiguredClient = new InstagramContentClient(
                new InstagramCollectionProperties("v24.0", "", ""),
                RestClient.create());

        assertThatThrownBy(() -> unconfiguredClient.collect(
                "nike", LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INSTAGRAM_COLLECTION_CONFIG_MISSING);
    }

    @Test
    @DisplayName("올바르지 않은 Instagram username을 거부한다")
    void rejectInvalidUsername() {
        assertThatThrownBy(() -> client.collect(
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

        assertThatThrownBy(() -> client.collect(
                "pharrell", LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INSTAGRAM_API_CALL_FAILED);
        server.verify();
    }
}
