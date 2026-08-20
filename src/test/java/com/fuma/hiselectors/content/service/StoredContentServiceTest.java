package com.fuma.hiselectors.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.client.ContentFetcher;
import com.fuma.hiselectors.content.client.dto.RawContent;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.service.GenerationService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoredContentServiceTest {

    @Mock
    private GenerationService generationService;

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private ContentFetcher instagramFetcher;

    @Mock
    private ContentFetcher youtubeFetcher;

    private StoredContentService service;

    @BeforeEach
    void setUp() {
        service = new StoredContentService(
                generationService,
                contentRepository,
                List.of(instagramFetcher, youtubeFetcher));
    }

    @Test
    void fetchesCurrentGenerationContentsByPlatform() {
        Generation generation = org.mockito.Mockito.mock(Generation.class);
        Content instagram = content(SnsPlatform.INSTAGRAM, "instagram-id");
        Content youtubeFound = content(SnsPlatform.YOUTUBE, "youtube-found");
        Content youtubeMissing = content(SnsPlatform.YOUTUBE, "youtube-missing");
        ContentFetcher.Engagement engagement =
                new ContentFetcher.Engagement(null, 20L, 3L, null);
        ContentFetcher.FetchResult instagramResult = new ContentFetcher.FetchResult(
                "instagram-id",
                ContentFetcher.FetchStatus.FOUND,
                org.mockito.Mockito.mock(RawContent.class),
                engagement);
        ContentFetcher.FetchResult youtubeFoundResult = new ContentFetcher.FetchResult(
                "youtube-found",
                ContentFetcher.FetchStatus.FOUND,
                org.mockito.Mockito.mock(RawContent.class),
                engagement);
        ContentFetcher.FetchResult youtubeMissingResult = new ContentFetcher.FetchResult(
                "youtube-missing",
                ContentFetcher.FetchStatus.NOT_FOUND,
                null,
                null);

        when(generationService.getActive()).thenReturn(generation);
        when(generation.getId()).thenReturn(3L);
        when(contentRepository.findAllByGenerationId(3L))
                .thenReturn(List.of(instagram, youtubeFound, youtubeMissing));
        when(instagramFetcher.supports()).thenReturn(SnsPlatform.INSTAGRAM);
        when(youtubeFetcher.supports()).thenReturn(SnsPlatform.YOUTUBE);
        when(instagramFetcher.fetchByContentIds(List.of("instagram-id")))
                .thenReturn(List.of(instagramResult));
        when(youtubeFetcher.fetchByContentIds(
                List.of("youtube-found", "youtube-missing")))
                .thenReturn(List.of(youtubeFoundResult, youtubeMissingResult));

        List<StoredContentService.StoredContentResult> result = service.fetchStoredContents();

        assertThat(result).containsExactly(
                new StoredContentService.StoredContentResult(instagram, instagramResult),
                new StoredContentService.StoredContentResult(youtubeFound, youtubeFoundResult),
                new StoredContentService.StoredContentResult(youtubeMissing, youtubeMissingResult));
    }

    private Content content(SnsPlatform platform, String snsContentId) {
        return Content.builder()
                .selectorsId(1L)
                .snsCode(platform)
                .snsContentId(snsContentId)
                .contentUrl("https://example.com/" + snsContentId)
                .contentType(ContentType.FEED)
                .build();
    }
}
