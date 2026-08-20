package com.fuma.hiselectors.content.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentEngagement;
import com.fuma.hiselectors.content.model.ContentType;
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
@Import(SelectorsDetailRepositoryTest.CacheConfig.class)
class SelectorsDetailRepositoryTest {

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private ContentEngagementRepository contentEngagementRepository;

    @Test
    void findsNonDeletedSelectorContentsNewestFirst() {
        Content oldest = saveContent("oldest");
        Content deleted = saveContent("deleted");
        deleted.markDeleted();
        contentRepository.saveAndFlush(deleted);
        Content newest = saveContent("newest");

        assertThat(contentRepository
                .findAllBySelectorsIdAndDeletedFalseOrderByCreatedAtDescIdDesc(1L))
                .extracting(Content::getId)
                .containsExactly(newest.getId(), oldest.getId());
    }

    @Test
    void findsOnlyLatestEngagementPerContent() {
        Content first = saveContent("first");
        Content second = saveContent("second");
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 12, 0);
        contentEngagementRepository.saveAllAndFlush(List.of(
                engagement(first.getId(), 10L, now.minusHours(1)),
                engagement(first.getId(), 20L, now),
                engagement(second.getId(), 30L, now)));

        assertThat(contentEngagementRepository.findLatestByContentIds(
                List.of(first.getId(), second.getId())))
                .extracting(ContentEngagement::getViewCount)
                .containsExactlyInAnyOrder(20L, 30L);
    }

    private Content saveContent(String snsContentId) {
        return contentRepository.saveAndFlush(Content.builder()
                .selectorsId(1L)
                .snsCode(SnsPlatform.YOUTUBE)
                .snsContentId(snsContentId)
                .contentUrl("https://example.com/" + snsContentId)
                .contentType(ContentType.SHORTS)
                .build());
    }

    private ContentEngagement engagement(
            Long contentId, Long viewCount, LocalDateTime createdAt) {
        return ContentEngagement.builder()
                .contentId(contentId)
                .viewCount(viewCount)
                .createdAt(createdAt)
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
