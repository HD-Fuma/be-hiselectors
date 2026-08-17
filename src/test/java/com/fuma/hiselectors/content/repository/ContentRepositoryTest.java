package com.fuma.hiselectors.content.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentType;
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
    @DisplayName("SNS 코드와 콘텐츠 ID 목록으로 기존 콘텐츠를 조회한다")
    void findAllBySnsCodeAndSnsContentIdIn() {
        contentRepository.saveAll(List.of(
                Content.builder()
                        .selectorsId(1L)
                        .snsCode(SnsPlatform.INSTAGRAM)
                        .snsContentId("instagram-001")
                        .contentUrl("https://www.instagram.com/p/instagram-001")
                        .contentType(ContentType.FEED)
                        .build(),
                Content.builder()
                        .selectorsId(1L)
                        .snsCode(SnsPlatform.YOUTUBE)
                        .snsContentId("youtube-001")
                        .contentUrl("https://www.youtube.com/watch?v=youtube-001")
                        .contentType(ContentType.LONG_FORM)
                        .build()));

        List<Content> found = contentRepository.findAllBySnsCodeAndSnsContentIdIn(
                SnsPlatform.INSTAGRAM,
                List.of("instagram-001", "youtube-001", "missing"));

        assertThat(found)
                .extracting(Content::getSnsContentId)
                .containsExactly("instagram-001");
    }
}
