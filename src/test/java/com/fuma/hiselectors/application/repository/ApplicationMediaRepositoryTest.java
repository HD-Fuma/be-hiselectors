package com.fuma.hiselectors.application.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.application.model.ApplicationMedia;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.ContentType;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(ApplicationMediaRepositoryTest.CacheConfig.class)
class ApplicationMediaRepositoryTest {

    @Autowired
    private ApplicationMediaRepository repository;

    @Autowired
    private EntityManager entityManager;

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

    @Test
    void findsCompleteSnapshotsForDetailAndPagedSummaries() {
        repository.save(media(1L, "post-2", 1));
        repository.save(media(1L, "post-1", 0));
        repository.save(media(2L, "other", 0));

        assertThat(repository.findAllByApplicationIdOrderBySequenceNoAsc(1L))
                .extracting(ApplicationMedia::getSnsContentId)
                .containsExactly("post-1", "post-2");
        assertThat(repository.findAllByApplicationIdInOrderByApplicationIdAscSequenceNoAsc(
                java.util.List.of(2L, 1L)))
                .extracting(ApplicationMedia::getSnsContentId)
                .containsExactly("post-1", "post-2", "other");
    }

    @Test
    void persistsAndCascadesEveryMediaAndThumbnailUrl() {
        ApplicationMedia saved = repository.saveAndFlush(ApplicationMedia.builder()
                .applicationId(1L)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId("carousel")
                .contentUrl("https://www.instagram.com/p/carousel")
                .mediaUrl("https://cdn.example.com/first.jpg")
                .mediaUrls(List.of(
                        "https://cdn.example.com/first.jpg",
                        "https://cdn.example.com/second.mp4"))
                .thumbnailUrls(List.of(
                        "https://cdn.example.com/second-thumbnail.jpg"))
                .contentType(ContentType.FEED)
                .sequenceNo(0)
                .publishedAt(LocalDateTime.now())
                .collectedAt(LocalDateTime.now())
                .build());
        entityManager.clear();

        ApplicationMedia stored = repository.findById(saved.getId()).orElseThrow();

        assertThat(stored.getMediaUrls()).containsExactly(
                "https://cdn.example.com/first.jpg",
                "https://cdn.example.com/second.mp4");
        assertThat(stored.getThumbnailUrls())
                .containsExactly("https://cdn.example.com/second-thumbnail.jpg");

        repository.deleteByApplicationId(1L);
        repository.flush();
        assertThat(((Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM application_media_url").getSingleResult()).longValue())
                .isZero();
    }

    private ApplicationMedia media(Long applicationId, String contentId, int sequenceNo) {
        return ApplicationMedia.builder()
                .applicationId(applicationId)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId(contentId)
                .mediaUrl("https://example.com/" + contentId)
                .contentType(ContentType.FEED)
                .sequenceNo(sequenceNo)
                .publishedAt(LocalDateTime.now().minusDays(sequenceNo))
                .collectedAt(LocalDateTime.now())
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
