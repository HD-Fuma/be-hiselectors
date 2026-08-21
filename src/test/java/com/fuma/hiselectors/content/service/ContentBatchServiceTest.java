package com.fuma.hiselectors.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

    @Test
    void returnsImmediatelyWithoutCallingChildServicesWhenAlreadyRunning() throws Exception {
        CountDownLatch collectionStarted = new CountDownLatch(1);
        CountDownLatch allowCollectionToFinish = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<ContentBatchService.ContentBatchResult> firstRun = null;
        try {
            when(newContentService.collect()).thenAnswer(invocation -> {
                collectionStarted.countDown();
                if (!allowCollectionToFinish.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("timed out waiting to finish collection");
                }
                return new NewContentService.NewContentResult(2, 0);
            });
            when(storedContentService.check()).thenReturn(
                    new StoredContentService.StoredContentResult(3, 0));

            firstRun = executor.submit(service::run);
            assertThat(collectionStarted.await(5, TimeUnit.SECONDS)).isTrue();

            ContentBatchService.ContentBatchResult busyResult = service.run();

            assertThat(busyResult).isEqualTo(
                    new ContentBatchService.ContentBatchResult(0, 0, false, false));
            verify(newContentService, times(1)).collect();
            verifyNoInteractions(storedContentService);

            allowCollectionToFinish.countDown();
            assertThat(firstRun.get(5, TimeUnit.SECONDS)).isEqualTo(
                    new ContentBatchService.ContentBatchResult(2, 3, true, true));
            verify(storedContentService).check();
        } finally {
            allowCollectionToFinish.countDown();
            if (firstRun != null && !firstRun.isDone()) {
                firstRun.cancel(true);
            }
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void releasesExecutionGuardAfterUnexpectedError() {
        when(newContentService.collect())
                .thenThrow(new AssertionError("unexpected"))
                .thenReturn(new NewContentService.NewContentResult(2, 0));
        when(storedContentService.check()).thenReturn(
                new StoredContentService.StoredContentResult(3, 0));

        assertThatThrownBy(service::run)
                .isInstanceOf(AssertionError.class)
                .hasMessage("unexpected");

        assertThat(service.run()).isEqualTo(
                new ContentBatchService.ContentBatchResult(2, 3, true, true));
        verify(newContentService, times(2)).collect();
        verify(storedContentService).check();
    }
}
