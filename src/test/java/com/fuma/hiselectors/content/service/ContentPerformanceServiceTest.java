package com.fuma.hiselectors.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.dto.ContentPerformanceQueryRow;
import com.fuma.hiselectors.content.dto.ContentPerformanceResponse;
import com.fuma.hiselectors.content.dto.ContentPerformanceSummaryResponse;
import com.fuma.hiselectors.content.dto.ContentFormatCountProjection;
import com.fuma.hiselectors.content.model.ContentEngagement;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.content.repository.ContentEngagementRepository;
import com.fuma.hiselectors.content.repository.ContentMediaRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.repository.GenerationRepository;
import com.fuma.hiselectors.generation.service.GenerationService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class ContentPerformanceServiceTest {

    private GenerationService generationService;
    private ContentRepository contentRepository;
    private ContentMediaRepository mediaRepository;
    private ContentEngagementRepository engagementRepository;
    private GenerationRepository generationRepository;
    private ContentPerformanceService service;

    @BeforeEach
    void setUp() {
        generationService = mock(GenerationService.class);
        contentRepository = mock(ContentRepository.class);
        mediaRepository = mock(ContentMediaRepository.class);
        engagementRepository = mock(ContentEngagementRepository.class);
        generationRepository = mock(GenerationRepository.class);
        service = new ContentPerformanceService(
                generationService, contentRepository, mediaRepository, engagementRepository,
                generationRepository);
    }

    @Test
    void returnsUploadAndFormatSummaryFromRepositories() {
        Generation current = mock(Generation.class);
        Generation previous = mock(Generation.class);
        when(current.getId()).thenReturn(20L);
        when(current.getGenerationName()).thenReturn("2기");
        when(previous.getId()).thenReturn(10L);
        when(previous.getGenerationName()).thenReturn("1기");
        when(generationService.getActive()).thenReturn(current);
        when(generationRepository.findAllByOrderByStartDateDescIdDesc())
                .thenReturn(List.of(current, previous));
        when(contentRepository.countByDeletedFalse()).thenReturn(59L);
        when(contentRepository.countByGenerationId(20L)).thenReturn(13L);
        when(contentRepository.countByGenerationId(10L)).thenReturn(10L);
        ContentFormatCountProjection reels = mock(ContentFormatCountProjection.class);
        when(reels.getContentType()).thenReturn(ContentType.SHORT_FORM);
        when(reels.getCount()).thenReturn(19L);
        when(contentRepository.countAllByContentType()).thenReturn(List.of(reels));

        ContentPerformanceSummaryResponse result = service.getSummary();

        assertThat(result.totalContentCount()).isEqualTo(59L);
        assertThat(result.currentGenerationName()).isEqualTo("2기");
        assertThat(result.currentGenerationContentCount()).isEqualTo(13L);
        assertThat(result.previousGenerationName()).isEqualTo("1기");
        assertThat(result.previousGenerationContentCount()).isEqualTo(10L);
        assertThat(result.formats()).singleElement().satisfies(format -> {
            assertThat(format.contentType()).isEqualTo(ContentType.SHORT_FORM);
            assertThat(format.count()).isEqualTo(19L);
        });
    }

    @Test
    void returnsLatestMetricsAndOrderedTrendForCurrentGeneration() {
        Generation generation = mock(Generation.class);
        when(generation.getId()).thenReturn(10L);
        when(generation.getGenerationName()).thenReturn("1기");
        when(generationService.getActive()).thenReturn(generation);
        LocalDateTime publishedAt = LocalDateTime.of(2026, 8, 18, 9, 0);
        ContentPerformanceQueryRow row = new ContentPerformanceQueryRow(
                1L, 11L, "셀렉터", SnsPlatform.INSTAGRAM, "post-1",
                "https://instagram.com/p/post-1", ContentType.SHORT_FORM,
                publishedAt, 101L, "selectors-account", 12000L,
                "https://cdn.example.com/profile.jpg");
        PageRequest pageable = PageRequest.of(0, 20);
        when(contentRepository.findPerformanceRowsByGenerationId(10L, pageable))
                .thenReturn(new PageImpl<>(List.of(row), pageable, 1));
        ContentMedia media = mock(ContentMedia.class);
        when(media.getContentVersionId()).thenReturn(101L);
        when(media.getMediaType()).thenReturn(MediaType.VIDEO);
        when(media.getMediaUrl()).thenReturn("https://cdn.example.com/reel.mp4");
        when(media.getThumbnailUrl()).thenReturn("https://cdn.example.com/reel.jpg");
        when(media.getSequenceNo()).thenReturn(0);
        when(mediaRepository
                .findAllByContentVersionIdInOrderByContentVersionIdAscSequenceNoAsc(
                        List.of(101L)))
                .thenReturn(List.of(media));
        when(engagementRepository.findAllByContentIdInOrderByContentIdAscCreatedAtAsc(
                List.of(1L)))
                .thenReturn(List.of(
                        engagement(1L, 100L, 10L, 1L, publishedAt.plusHours(1)),
                        engagement(1L, 300L, 30L, 3L, publishedAt.plusHours(2))));

        Page<ContentPerformanceResponse> result =
                service.getCurrentGenerationPerformance(0, 20);

        assertThat(result.getContent()).singleElement().satisfies(item -> {
            assertThat(item.contentId()).isEqualTo(1L);
            assertThat(item.selectorsNickname()).isEqualTo("셀렉터");
            assertThat(item.generationName()).isEqualTo("1기");
            assertThat(item.followerCount()).isEqualTo(12000L);
            assertThat(item.viewCount()).isEqualTo(300L);
            assertThat(item.likeCount()).isEqualTo(30L);
            assertThat(item.commentCount()).isEqualTo(3L);
            assertThat(item.media()).singleElement().satisfies(itemMedia -> {
                assertThat(itemMedia.mediaType()).isEqualTo(MediaType.VIDEO);
                assertThat(itemMedia.mediaUrl()).isEqualTo("https://cdn.example.com/reel.mp4");
                assertThat(itemMedia.thumbnailUrl()).isEqualTo("https://cdn.example.com/reel.jpg");
            });
            assertThat(item.trend()).extracting(ContentPerformanceResponse.TrendPoint::viewCount)
                    .containsExactly(100L, 300L);
        });
        verify(contentRepository).findPerformanceRowsByGenerationId(10L, pageable);
    }

    private ContentEngagement engagement(
            Long contentId,
            Long views,
            Long likes,
            Long comments,
            LocalDateTime recordedAt) {
        return ContentEngagement.builder()
                .contentId(contentId)
                .viewCount(views)
                .likeCount(likes)
                .commentCount(comments)
                .createdAt(recordedAt)
                .build();
    }
}
