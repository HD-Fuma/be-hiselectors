package com.fuma.hiselectors.application.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.application.model.ApplicationMedia;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.content.model.MediaType;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
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
    void findsLatestThreeAssetsInPostAndAssetOrder() {
        repository.save(media(1L, "post-2", "media-3", 1, 0));
        repository.save(media(1L, "post-1", "media-2", 0, 1));
        repository.save(media(1L, "post-1", "media-1", 0, 0));
        repository.save(media(1L, "post-3", "media-4", 2, 0));
        repository.save(media(2L, "other", "other-media", 0, 0));

        var result = repository
                .findTop3ByApplicationIdOrderBySequenceNoAscMediaSequenceNoAsc(1L);

        assertThat(result)
                .extracting(media -> media.getSnsContentId() + ":" + media.getSnsMediaId())
                .containsExactly("post-1:media-1", "post-1:media-2", "post-2:media-3");
    }

    @Test
    void findsCompleteSnapshotsForDetailAndPagedSummaries() {
        repository.save(media(1L, "post-2", "media-3", 1, 0));
        repository.save(media(1L, "post-1", "media-2", 0, 1));
        repository.save(media(1L, "post-1", "media-1", 0, 0));
        repository.save(media(2L, "other", "other-media", 0, 0));

        assertThat(repository
                .findAllByApplicationIdOrderBySequenceNoAscMediaSequenceNoAsc(1L))
                .extracting(ApplicationMedia::getSnsMediaId)
                .containsExactly("media-1", "media-2", "media-3");
        assertThat(repository
                .findAllByApplicationIdInOrderByApplicationIdAscSequenceNoAscMediaSequenceNoAsc(
                        java.util.List.of(2L, 1L)))
                .extracting(ApplicationMedia::getSnsMediaId)
                .containsExactly("media-1", "media-2", "media-3", "other-media");
    }

    @Test
    void persistsOneMediaAssetPerRow() {
        ApplicationMedia saved = repository.saveAndFlush(ApplicationMedia.builder()
                .applicationId(1L)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId("carousel")
                .snsMediaId("carousel-video")
                .contentUrl("https://www.instagram.com/p/carousel")
                .mediaUrl("https://cdn.example.com/second.mp4")
                .thumbnailUrl("https://cdn.example.com/second-thumbnail.jpg")
                .contentType(ContentType.REELS)
                .mediaType(MediaType.VIDEO)
                .caption("caption")
                .sequenceNo(0)
                .mediaSequenceNo(1)
                .publishedAt(LocalDateTime.now())
                .collectedAt(LocalDateTime.now())
                .build());
        entityManager.clear();

        ApplicationMedia stored = repository.findById(saved.getId()).orElseThrow();

        assertThat(stored.getSnsContentId()).isEqualTo("carousel");
        assertThat(stored.getSnsMediaId()).isEqualTo("carousel-video");
        assertThat(stored.getMediaUrl()).isEqualTo("https://cdn.example.com/second.mp4");
        assertThat(stored.getThumbnailUrl())
                .isEqualTo("https://cdn.example.com/second-thumbnail.jpg");
        assertThat(stored.getMediaSequenceNo()).isEqualTo(1);
        assertThat(stored.getMediaType()).isEqualTo(MediaType.VIDEO);
    }

    private ApplicationMedia media(
            Long applicationId,
            String contentId,
            String mediaId,
            int sequenceNo,
            int mediaSequenceNo) {
        return ApplicationMedia.builder()
                .applicationId(applicationId)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId(contentId)
                .snsMediaId(mediaId)
                .mediaUrl("https://example.com/" + mediaId)
                .contentType(ContentType.POST)
                .mediaType(MediaType.IMAGE)
                .sequenceNo(sequenceNo)
                .mediaSequenceNo(mediaSequenceNo)
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
