package com.fuma.hiselectors.application.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.application.model.ApplicationMedia;
import com.fuma.hiselectors.application.model.SnsPlatform;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(ApplicationMediaRepositoryTest.CacheConfig.class)
class ApplicationMediaRepositoryTest {

    @Autowired
    private ApplicationMediaRepository repository;

    @Test
    void findsLatestThreeBySequence() {
        repository.save(media(1L, "post-4", 3));
        repository.save(media(1L, "post-2", 1));
        repository.save(media(1L, "post-1", 0));
        repository.save(media(1L, "post-3", 2));
        repository.save(media(2L, "other", 0));

        var result = repository.findTop3ByApplicationIdOrderBySequenceNoAsc(1L);

        assertThat(result)
                .extracting(ApplicationMedia::getSnsContentId)
                .containsExactly("post-1", "post-2", "post-3");
    }

    private ApplicationMedia media(Long applicationId, String contentId, int sequenceNo) {
        return ApplicationMedia.builder()
                .applicationId(applicationId)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId(contentId)
                .mediaUrl("https://example.com/" + contentId)
                .sequenceNo(sequenceNo)
                .publishedAt(LocalDateTime.now().minusDays(sequenceNo))
                .build();
    }

    @TestConfiguration
    static class CacheConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }
}
