package com.fuma.hiselectors.content.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentEngagement;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.content.model.ContentVersion;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
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
        ContentVersion version = contentVersionRepository.save(ContentVersion.builder()
                .contentId(content.getId())
                .versionNo(1L)
                .contentHash("a".repeat(64))
                .createdAt(collectedAt)
                .build());

        contentMediaRepository.saveAll(List.of(
                ContentMedia.builder()
                        .contentVersionId(version.getId())
                        .mediaType(ContentMedia.MediaType.TEXT)
                        .body("selectors content body")
                        .sequenceNo(0)
                        .build(),
                ContentMedia.builder()
                        .contentVersionId(version.getId())
                        .mediaType(ContentMedia.MediaType.IMAGE)
                        .mediaUrl("https://cdn.example.com/media-1.jpg")
                        .snsMediaId("image-1")
                        .sequenceNo(1)
                        .build(),
                ContentMedia.builder()
                        .contentVersionId(version.getId())
                        .mediaType(ContentMedia.MediaType.VIDEO)
                        .snsMediaId("video-1")
                        .sequenceNo(2)
                        .build()));

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
                        tuple(0, ContentMedia.MediaType.TEXT, null),
                        tuple(1, ContentMedia.MediaType.IMAGE, "image-1"),
                        tuple(2, ContentMedia.MediaType.VIDEO, "video-1"));
        assertThat(contentEngagementRepository.findAll())
                .singleElement()
                .extracting(ContentEngagement::getViewCount,
                        ContentEngagement::getLikeCount,
                        ContentEngagement::getCommentCount)
                .containsExactly(100L, 20L, 3L);
    }

    @Test
    void rejectDuplicateSequenceNoWithinContentVersion() {
        contentMediaRepository.saveAndFlush(ContentMedia.builder()
                .contentVersionId(1L)
                .mediaType(ContentMedia.MediaType.TEXT)
                .body("first")
                .sequenceNo(0)
                .build());

        assertThatThrownBy(() -> contentMediaRepository.saveAndFlush(ContentMedia.builder()
                .contentVersionId(1L)
                .mediaType(ContentMedia.MediaType.IMAGE)
                .snsMediaId("image-1")
                .sequenceNo(0)
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
