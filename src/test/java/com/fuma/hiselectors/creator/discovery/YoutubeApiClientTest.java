package com.fuma.hiselectors.creator.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuma.hiselectors.creator.discovery.dto.YoutubeChannelListResponse;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

class YoutubeApiClientTest {

    @Test
    @DisplayName("이번 달 필터는 서울 시간 기준 월초를 YouTube 검색에 전달한다")
    void filtersSearchFromCurrentMonth() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        YoutubeApiClient client = new YoutubeApiClient(
                new YoutubeDiscoveryProperties("test-api-key", null, null));
        ReflectionTestUtils.setField(client, "restClient", builder.build());

        server.expect(request -> {
            String publishedAfter = org.springframework.web.util.UriComponentsBuilder
                    .fromUri(request.getURI()).build().getQueryParams()
                    .getFirst("publishedAfter");
            var seoulStart = Instant.parse(publishedAfter)
                    .atZone(ZoneId.of("Asia/Seoul"));
            assertThat(YearMonth.from(seoulStart)).isEqualTo(YearMonth.now(
                    ZoneId.of("Asia/Seoul")));
            assertThat(seoulStart.getDayOfMonth()).isEqualTo(1);
            assertThat(seoulStart.toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
        }).andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));

        assertThat(client.discoverByKeyword("겟레디윗미", 25, true)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("최근 활동일은 키워드 검색 영상이 아니라 채널의 최신 업로드로 계산한다")
    void useLatestChannelUploadForLastContentAt() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        YoutubeApiClient client = new YoutubeApiClient(
                new YoutubeDiscoveryProperties("test-api-key", null, null));
        ReflectionTestUtils.setField(client, "restClient", builder.build());
        OffsetDateTime latestUpload = OffsetDateTime.now(ZoneOffset.UTC)
                .minusDays(1)
                .withNano(0);
        OffsetDateTime previousUpload = latestUpload.minusDays(1);

        server.expect(request -> assertThat(request.getURI().getPath())
                        .isEqualTo("/youtube/v3/search"))
                .andRespond(withSuccess("""
                        {"items":[{"id":{"videoId":"matched-video"}}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(request -> assertThat(request.getURI().getPath())
                        .isEqualTo("/youtube/v3/videos"))
                .andRespond(withSuccess("""
                        {"items":[{
                          "id":"matched-video",
                          "snippet":{
                            "channelId":"channel-id",
                            "publishedAt":"2023-07-28T09:00:18Z"
                          },
                          "statistics":{
                            "viewCount":"100",
                            "likeCount":"10",
                            "commentCount":"1"
                          }
                        }]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(request -> assertThat(request.getURI().getPath())
                        .isEqualTo("/youtube/v3/channels"))
                .andRespond(withSuccess("""
                        {"items":[{
                          "id":"channel-id",
                          "snippet":{"title":"creator","description":""},
                          "statistics":{"subscriberCount":"1000","viewCount":"10000"},
                          "contentDetails":{"relatedPlaylists":{"uploads":"uploads-id"}}
                        }]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(request -> assertThat(request.getURI().getPath())
                        .isEqualTo("/youtube/v3/playlistItems"))
                .andRespond(withSuccess("""
                        {"items":[
                          {"contentDetails":{"videoPublishedAt":"%s"}},
                          {"contentDetails":{"videoPublishedAt":"%s"}}
                        ]}
                        """.formatted(previousUpload, latestUpload), MediaType.APPLICATION_JSON));

        var result = client.discoverByKeyword("겟레디윗미", 25);

        assertThat(result).singleElement().satisfies(channel -> {
            assertThat(channel.lastUploadAt()).isEqualTo(latestUpload
                    .atZoneSameInstant(ZoneId.of("Asia/Seoul"))
                    .toLocalDateTime());
            assertThat(channel.recent90DayContentCount()).isEqualTo(2);
        });
        assertThat(client.consumedQuota()).isEqualTo(103);
        server.verify();
    }

    @Test
    @DisplayName("YouTube 채널 응답의 공개 프로필 이미지를 매핑한다")
    void mapsChannelProfileImage() throws Exception {
        YoutubeChannelListResponse response = new ObjectMapper().readValue("""
                {"items":[{"snippet":{"thumbnails":{
                  "default":{"url":"https://yt.example/default.jpg"},
                  "high":{"url":"https://yt.example/high.jpg"}
                }}}]}
                """, YoutubeChannelListResponse.class);
        YoutubeApiClient client = new YoutubeApiClient(
                new YoutubeDiscoveryProperties(null, null, null));

        String imageUrl = ReflectionTestUtils.invokeMethod(
                client, "profileImageUrl", response.items().getFirst().snippet());

        assertThat(imageUrl).isEqualTo("https://yt.example/high.jpg");
    }

    @Test
    @DisplayName("발굴을 시작할 때 이전 실행의 쿼터 사용량을 초기화한다")
    void resetConsumedQuotaForEachRun() {
        YoutubeApiClient client = new YoutubeApiClient(
                new YoutubeDiscoveryProperties(null, null, null));
        ReflectionTestUtils.setField(client, "consumedQuota", 102);

        assertThatThrownBy(() -> client.discoverByKeyword("겟레디윗미", 25))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.YOUTUBE_API_KEY_MISSING);
        assertThat(client.consumedQuota()).isZero();
    }
}
