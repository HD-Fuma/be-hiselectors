package com.fuma.hiselectors.content.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class ContentRepositoryTest {

    @Autowired
    private ContentRepository contentRepository;

    @Test
    @DisplayName("SNS 코드와 콘텐츠 ID로 저장된 콘텐츠를 찾는다")
    void findBySnsCodeAndSnsContentId() {
        contentRepository.save(Content.builder()
                .selectorsId(1L)
                .snsCode(SnsPlatform.YOUTUBE)
                .snsContentId("video-001")
                .contentUrl("https://www.youtube.com/watch?v=video-001")
                .contentType(ContentType.LONG_FORM)
                .build());

        assertThat(contentRepository.existsBySnsCodeAndSnsContentId(
                SnsPlatform.YOUTUBE, "video-001")).isTrue();
        assertThat(contentRepository.findBySnsCodeAndSnsContentId(
                SnsPlatform.YOUTUBE, "video-001")).isPresent();
        assertThat(contentRepository.existsBySnsCodeAndSnsContentId(
                SnsPlatform.INSTAGRAM, "video-001")).isFalse();
    }

    @Test
    @DisplayName("콘텐츠의 최초 버전 번호는 1이다")
    void initialVersionNumberIsOne() {
        Content saved = contentRepository.save(Content.builder()
                .selectorsId(1L)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId("media-001")
                .contentUrl("https://www.instagram.com/p/media-001")
                .contentType(ContentType.FEED)
                .build());

        assertThat(saved.getLastVersionNo()).isEqualTo(1L);
    }

    @Test
    @DisplayName("셀렉터와 SNS 플랫폼이 같고 기수 시작 이후 저장된 콘텐츠를 조회한다")
    void findContentsStoredFromGenerationStart() {
        Content active = Content.builder()
                .selectorsId(1L)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId("instagram-active")
                .contentUrl("https://www.instagram.com/p/instagram-active")
                .contentType(ContentType.FEED)
                .build();
        Content deleted = Content.builder()
                .selectorsId(1L)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId("instagram-deleted")
                .contentUrl("https://www.instagram.com/p/instagram-deleted")
                .contentType(ContentType.FEED)
                .build();
        deleted.markDeleted();
        Content otherSelector = Content.builder()
                .selectorsId(2L)
                .snsCode(SnsPlatform.INSTAGRAM)
                .snsContentId("other-selector")
                .contentUrl("https://www.instagram.com/p/other-selector")
                .contentType(ContentType.FEED)
                .build();
        Content otherPlatform = Content.builder()
                .selectorsId(1L)
                .snsCode(SnsPlatform.YOUTUBE)
                .snsContentId("other-platform")
                .contentUrl("https://www.youtube.com/watch?v=other-platform")
                .contentType(ContentType.LONG_FORM)
                .build();
        contentRepository.saveAllAndFlush(
                List.of(active, deleted, otherSelector, otherPlatform));

        List<Content> found = contentRepository
                .findAllBySelectorsIdAndSnsCodeAndCreatedAtGreaterThanEqual(
                        1L, SnsPlatform.INSTAGRAM,
                        LocalDateTime.of(2000, 1, 1, 0, 0));

        assertThat(found)
                .extracting(Content::getSnsContentId)
                .containsExactlyInAnyOrder("instagram-active", "instagram-deleted");

        List<Content> storedBeforeGeneration = contentRepository
                .findAllBySelectorsIdAndSnsCodeAndCreatedAtGreaterThanEqual(
                        1L, SnsPlatform.INSTAGRAM,
                        LocalDateTime.now().plusDays(1));

        assertThat(storedBeforeGeneration).isEmpty();
    }
}
