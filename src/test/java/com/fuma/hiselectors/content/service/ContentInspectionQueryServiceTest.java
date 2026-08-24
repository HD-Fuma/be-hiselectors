package com.fuma.hiselectors.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.dto.ContentInspectionListItemResponse;
import com.fuma.hiselectors.content.dto.ContentInspectionQueryRow;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.content.repository.ContentMediaRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.service.GenerationService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class ContentInspectionQueryServiceTest {

    private GenerationService generationService;
    private ContentRepository contentRepository;
    private ContentMediaRepository mediaRepository;
    private ContentInspectionQueryService service;

    @BeforeEach
    void setUp() {
        generationService = mock(GenerationService.class);
        contentRepository = mock(ContentRepository.class);
        mediaRepository = mock(ContentMediaRepository.class);
        service = new ContentInspectionQueryService(
                generationService, contentRepository, mediaRepository);
    }

    @Test
    void mapsOrderedTextsAndMediaFromOneBatchQuery() {
        Generation generation = mock(Generation.class);
        when(generation.getId()).thenReturn(10L);
        when(generation.getGenerationName()).thenReturn("1기");
        when(generationService.getActive()).thenReturn(generation);

        LocalDateTime storedAt = LocalDateTime.of(2026, 8, 18, 9, 0);
        LocalDateTime versionStoredAt = LocalDateTime.of(2026, 8, 18, 9, 5);
        ContentInspectionQueryRow row = new ContentInspectionQueryRow(
                1L, 11L, "셀렉터", SnsPlatform.INSTAGRAM, "post-1",
                "https://instagram.com/p/post-1", ContentType.FEED, storedAt,
                101L, 2L, null, null, null, versionStoredAt,
                "selectors-account", "https://cdn.example.com/profile.jpg");
        PageRequest pageable = PageRequest.of(0, 20);
        when(contentRepository.findInspectionRowsByGenerationId(10L, pageable))
                .thenReturn(new PageImpl<>(List.of(row), pageable, 1));
        when(mediaRepository
                .findAllByContentVersionIdInOrderByContentVersionIdAscSequenceNoAsc(
                        List.of(101L)))
                .thenReturn(List.of(
                        media(101L, MediaType.TEXT, null, null, 0, "제목"),
                        media(101L, MediaType.TEXT, null, null, 1, "본문"),
                        media(101L, MediaType.IMAGE,
                                "https://cdn.example.com/image.jpg", "image-1", 2, null),
                        media(101L, MediaType.VIDEO, null, "video-1", 3, null)));

        Page<ContentInspectionListItemResponse> result =
                service.getCurrentGenerationContents(0, 20);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).singleElement().satisfies(item -> {
            assertThat(item.contentId()).isEqualTo(1L);
            assertThat(item.selectorsId()).isEqualTo(11L);
            assertThat(item.selectorsNickname()).isEqualTo("셀렉터");
            assertThat(item.snsCode()).isEqualTo(SnsPlatform.INSTAGRAM);
            assertThat(item.snsContentId()).isEqualTo("post-1");
            assertThat(item.contentUrl()).isEqualTo("https://instagram.com/p/post-1");
            assertThat(item.contentType()).isEqualTo(ContentType.FEED);
            assertThat(item.storedAt()).isEqualTo(storedAt);
            assertThat(item.latestVersionId()).isEqualTo(101L);
            assertThat(item.latestVersionNo()).isEqualTo(2L);
            assertThat(item.inspectionStatus()).isNull();
            assertThat(item.inspectedAt()).isNull();
            assertThat(item.latestVersionStoredAt()).isEqualTo(versionStoredAt);
            assertThat(item.accountId()).isEqualTo("selectors-account");
            assertThat(item.profileImageUrl())
                    .isEqualTo("https://cdn.example.com/profile.jpg");
            assertThat(item.generationName()).isEqualTo("1기");
            assertThat(item.texts()).containsExactly("제목", "본문");
            assertThat(item.media())
                    .extracting(
                            media -> media.mediaType(),
                            media -> media.mediaUrl(),
                            media -> media.snsMediaId(),
                            media -> media.sequenceNo())
                    .containsExactly(
                            tuple(MediaType.IMAGE,
                                    "https://cdn.example.com/image.jpg", "image-1", 2),
                            tuple(MediaType.VIDEO, null, "video-1", 3));
        });
        verify(generationService).getActive();
        verify(contentRepository).findInspectionRowsByGenerationId(10L, pageable);
        verify(mediaRepository)
                .findAllByContentVersionIdInOrderByContentVersionIdAscSequenceNoAsc(
                        eq(List.of(101L)));
        verifyNoMoreInteractions(generationService, contentRepository, mediaRepository);
    }

    @Test
    void skipsMediaQueryForEmptyPage() {
        Generation generation = mock(Generation.class);
        when(generation.getId()).thenReturn(10L);
        when(generationService.getActive()).thenReturn(generation);
        PageRequest pageable = PageRequest.of(0, 20);
        when(contentRepository.findInspectionRowsByGenerationId(10L, pageable))
                .thenReturn(Page.empty(pageable));

        Page<ContentInspectionListItemResponse> result =
                service.getCurrentGenerationContents(0, 20);

        assertThat(result).isEmpty();
        verify(generationService).getActive();
        verify(contentRepository).findInspectionRowsByGenerationId(10L, pageable);
        verify(mediaRepository, never())
                .findAllByContentVersionIdInOrderByContentVersionIdAscSequenceNoAsc(
                        org.mockito.ArgumentMatchers.anyCollection());
    }

    private ContentMedia media(
            Long versionId,
            MediaType type,
            String url,
            String snsMediaId,
            int sequenceNo,
            String body) {
        return ContentMedia.create(
                versionId,
                type,
                url,
                snsMediaId,
                sequenceNo,
                body == null ? Map.of() : Map.of("text", body));
    }
}
