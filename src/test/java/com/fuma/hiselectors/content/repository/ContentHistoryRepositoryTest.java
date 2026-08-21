package com.fuma.hiselectors.content.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.config.CacheConfig;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentEngagement;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.model.MediaType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(CacheConfig.class)
class ContentHistoryRepositoryTest {

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private ContentVersionRepository contentVersionRepository;

    @Autowired
    private ContentMediaRepository contentMediaRepository;

    @Autowired
    private ContentEngagementRepository contentEngagementRepository;

    @Test
    void saveContentHistory() {
        Content content = contentRepository.save(Content.builder()
                .selectorsId(1L)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId("sns-content-1")
                .contentUrl("https://www.instagram.com/p/content-1")
                .contentType(ContentType.FEED)
                .build());

        LocalDateTime collectedAt = LocalDateTime.of(2026, 8, 13, 0, 0);
        ContentVersion version = contentVersionRepository.save(ContentVersion.create(
                content.getId(), 1L, "a".repeat(64), collectedAt));

        ContentMedia image = ContentMedia.create(
                version.getId(), MediaType.IMAGE, "https://cdn.example.com/media-1.jpg",
                "image-1", 1, Map.of());
        ContentMedia video = ContentMedia.create(
                version.getId(), MediaType.VIDEO, null, "video-1", 2, Map.of());
        contentMediaRepository.saveAll(List.of(
                ContentMedia.create(
                        version.getId(), MediaType.TEXT, null, null, 0,
                        Map.of("text", "selectors content body")),
                image,
                video));

        contentEngagementRepository.save(ContentEngagement.builder()
                .contentId(content.getId())
                .viewCount(100L)
                .likeCount(20L)
                .commentCount(3L)
                .shareCount(1L)
                .createdAt(collectedAt)
                .build());

        assertThat(contentVersionRepository.findById(version.getId()))
                .get()
                .extracting(ContentVersion::getContentHash)
                .isEqualTo("a".repeat(64));
        assertThat(contentMediaRepository
                .findByContentVersionIdOrderBySequenceNoAsc(version.getId()))
                .extracting(ContentMedia::getSequenceNo, ContentMedia::getMediaType,
                        ContentMedia::getSnsMediaId)
                .containsExactly(
                        tuple(0, MediaType.TEXT, null),
                        tuple(1, MediaType.IMAGE, "image-1"),
                        tuple(2, MediaType.VIDEO, "video-1"));
        assertThat(contentEngagementRepository.findAll())
                .singleElement()
                .extracting(ContentEngagement::getViewCount,
                        ContentEngagement::getLikeCount,
                        ContentEngagement::getCommentCount)
                .containsExactly(100L, 20L, 3L);
    }

}
