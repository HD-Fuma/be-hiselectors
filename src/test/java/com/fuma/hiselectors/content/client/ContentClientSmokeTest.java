package com.fuma.hiselectors.content.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.client.ContentPlatformClient.CollectionResult;
import com.fuma.hiselectors.content.client.dto.RawContent;
import com.fuma.hiselectors.content.config.InstagramCollectionProperties;
import com.fuma.hiselectors.content.config.YoutubeCollectionProperties;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 실제 Meta Graph API 및 YouTube Data API 수동 연동 테스트.
 *
 * <p>실행 조건: {@code RUN_CONTENT_API_TEST=true} 환경변수.
 * DB 저장 없는 외부 API 호출 및 RawContent 변환 검증.
 */
@Tag("external-api")
@EnabledIfEnvironmentVariable(named = "RUN_CONTENT_API_TEST", matches = "true")
@ActiveProfiles("local")
@SpringBootTest(
        classes = ContentClientSmokeTest.SmokeTestConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ContentClientSmokeTest {

    private static final String INSTAGRAM_USERNAME = "y__njin_";
    private static final String YOUTUBE_CHANNEL_ID = "UCQIUpdrt5UOD2ykffc1wjBA";
    private static final LocalDateTime CONTENT_COLLECTION_START_AT =
            LocalDateTime.of(2026, 5, 1, 0, 0);

    @Autowired
    private InstagramContentClient instagramContentClient;

    @Autowired
    private YoutubeContentClient youtubeContentClient;

    @Test
    void collectInstagramContentsFromRealApi() {
        CollectionResult result = instagramContentClient.collect(
                INSTAGRAM_USERNAME, CONTENT_COLLECTION_START_AT);

        assertRawContents(result.contents(), SnsPlatform.INSTAGRAM);
    }

    @Test
    void collectYoutubeContentsFromRealApi() {
        CollectionResult result = youtubeContentClient.collect(
                YOUTUBE_CHANNEL_ID, CONTENT_COLLECTION_START_AT);

        assertRawContents(result.contents(), SnsPlatform.YOUTUBE);
    }

    private void assertRawContents(List<RawContent> contents, SnsPlatform platform) {
        assertThat(contents).isNotEmpty();
        assertThat(contents).allSatisfy(content -> {
            assertThat(content.snsCode()).isEqualTo(platform);
            assertThat(content.snsContentId()).isNotBlank();
            assertThat(content.contentUrl()).isNotBlank();
            assertThat(content.createdAt()).isNotNull();
            assertThat(content.media()).isNotEmpty();
        });
    }

    @SpringBootConfiguration
    @EnableConfigurationProperties({
            InstagramCollectionProperties.class,
            YoutubeCollectionProperties.class
    })
    @Import({
            ContentClientConfig.class,
            InstagramContentClient.class,
            YoutubeContentClient.class
    })
    static class SmokeTestConfiguration {
    }
}
