package com.fuma.hiselectors.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentBatchServiceTest {

    @Mock
    private NewContentService newContentService;

    @Mock
    private StoredContentService storedContentService;

    @InjectMocks
    private ContentBatchService service;

    @Test
    void runsNewCollectionBeforeStoredContentCheck() {
        when(newContentService.collect()).thenReturn(
                new NewContentService.NewContentResult(2, 0));
        when(storedContentService.check()).thenReturn(
                new StoredContentService.StoredContentResult(3, 0));

        ContentBatchService.ContentBatchResult result = service.run();

        InOrder order = inOrder(newContentService, storedContentService);
        order.verify(newContentService).collect();
        order.verify(storedContentService).check();
        assertThat(result).isEqualTo(
                new ContentBatchService.ContentBatchResult(2, 3, true, true));
    }

    @Test
    void continuesStoredContentCheckWhenNewCollectionFails() {
        when(newContentService.collect()).thenThrow(new IllegalStateException("failed"));
        when(storedContentService.check()).thenReturn(
                new StoredContentService.StoredContentResult(3, 0));

        ContentBatchService.ContentBatchResult result = service.run();

        verify(storedContentService).check();
        assertThat(result).isEqualTo(
                new ContentBatchService.ContentBatchResult(0, 3, false, true));
    }

    @Test
    void preservesSavedCountButMarksNewCollectionFailedForAccountFailures() {
        when(newContentService.collect()).thenReturn(
                new NewContentService.NewContentResult(1, 1));
        when(storedContentService.check()).thenReturn(
                new StoredContentService.StoredContentResult(3, 0));

        ContentBatchService.ContentBatchResult result = service.run();

        assertThat(result).isEqualTo(
                new ContentBatchService.ContentBatchResult(1, 3, false, true));
    }

    @Test
    void preservesEngagementCountButMarksStoredContentFailedForContentFailures() {
        when(newContentService.collect()).thenReturn(
                new NewContentService.NewContentResult(2, 0));
        when(storedContentService.check()).thenReturn(
                new StoredContentService.StoredContentResult(3, 1));

        ContentBatchService.ContentBatchResult result = service.run();

        assertThat(result).isEqualTo(
                new ContentBatchService.ContentBatchResult(2, 3, true, false));
    }
}
