package com.fuma.hiselectors.creator.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuma.hiselectors.creator.discovery.dto.YoutubeChannelListResponse;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class YoutubeApiClientTest {

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
