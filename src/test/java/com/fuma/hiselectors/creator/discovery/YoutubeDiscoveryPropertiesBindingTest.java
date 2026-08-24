package com.fuma.hiselectors.creator.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.creator.discovery.scheduler.YoutubeDiscoveryBatchProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class YoutubeDiscoveryPropertiesBindingTest {

    @Test
    void applicationYaml이_수집_환경변수를_발굴_설정으로_연결한다() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(TestConfiguration.class)
                .withPropertyValues(
                        "YOUTUBE_API_KEY=youtube-api-key",
                        "YOUTUBE_DISCOVERY_DAILY_QUOTA=1234",
                        "YOUTUBE_DISCOVERY_MAX_RESULTS=17",
                        "YOUTUBE_DISCOVERY_MAX_KEYWORDS=8")
                .run(context -> {
                    YoutubeDiscoveryProperties discovery =
                            context.getBean(YoutubeDiscoveryProperties.class);
                    YoutubeDiscoveryBatchProperties batch =
                            context.getBean(YoutubeDiscoveryBatchProperties.class);

                    assertThat(discovery.apiKey()).isEqualTo("youtube-api-key");
                    assertThat(discovery.dailyQuota()).isEqualTo(1234);
                    assertThat(discovery.maxResultsPerKeyword()).isEqualTo(17);
                    assertThat(batch.maxKeywordsPerRun()).isEqualTo(8);
                });
    }

    @EnableConfigurationProperties({
            YoutubeDiscoveryProperties.class,
            YoutubeDiscoveryBatchProperties.class
    })
    static class TestConfiguration {
    }
}
