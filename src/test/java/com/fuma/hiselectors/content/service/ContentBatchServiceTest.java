package com.fuma.hiselectors.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.inspection.service.StaleContentInspectionService;
import com.fuma.hiselectors.logging.BatchEventLogger;
import com.fuma.hiselectors.logging.BatchLogContext;
import com.fuma.hiselectors.taskrun.service.TaskProgressReporter;
import java.util.Map;
import java.util.function.Consumer;
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

    @Mock
    private StaleContentInspectionService staleContentInspectionService;

    @Mock
    private BatchEventLogger batchEventLogger;

    @Mock
    private BatchLogContext batchLogContext;

    @Mock
    private TaskProgressReporter progress;

    @InjectMocks
    private ContentBatchService service;

    @Test
    void logsOneCombinedPartialFailureEventForInstagramAndYoutube() {
        when(batchEventLogger.start("content-sync")).thenReturn(batchLogContext);
        when(newContentService.collect(any())).thenReturn(new NewContentService.NewContentResult(
                9,
                1,
                Map.of(
                        SnsPlatform.INSTAGRAM,
                        new NewContentService.PlatformCollectionStats(14, 6, 6, 0),
                        SnsPlatform.YOUTUBE,
                        new NewContentService.PlatformCollectionStats(7, 3, 3, 1))));
        when(storedContentService.check(any())).thenReturn(
                new StoredContentService.StoredContentResult(
                        4,
                        2,
                        Map.of(
                                SnsPlatform.INSTAGRAM,
                                new StoredContentService.PlatformStoredContentStats(2, 0),
                                SnsPlatform.YOUTUBE,
                                new StoredContentService.PlatformStoredContentStats(1, 2))));

        service.run(progress);

        verify(batchEventLogger).partialFailure(
                batchLogContext,
                Map.ofEntries(
                        Map.entry("instagramNewCandidateCount", 14L),
                        Map.entry("instagramSelectorsContentCount", 6L),
                        Map.entry("instagramChangedContentCount", 2L),
                        Map.entry("instagramSavedVersionCount", 8L),
                        Map.entry("instagramFailedCount", 0L),
                        Map.entry("youtubeNewCandidateCount", 7L),
                        Map.entry("youtubeSelectorsContentCount", 3L),
                        Map.entry("youtubeChangedContentCount", 1L),
                        Map.entry("youtubeSavedVersionCount", 4L),
                        Map.entry("youtubeFailedCount", 3L),
                        Map.entry("failedStageCount", 0L)),
                Map.of());
        verify(batchEventLogger).start("content-sync");
        verifyNoMoreInteractions(batchEventLogger);
    }

    @Test
    void logsSuccessWhenContentSyncCompletesWithoutFailure() {
        when(batchEventLogger.start("content-sync")).thenReturn(batchLogContext);
        when(newContentService.collect(any())).thenReturn(new NewContentService.NewContentResult(
                1,
                0,
                Map.of(
                        SnsPlatform.INSTAGRAM,
                        new NewContentService.PlatformCollectionStats(2, 1, 1, 0))));
        when(storedContentService.check(any())).thenReturn(
                new StoredContentService.StoredContentResult(
                        1,
                        0,
                        Map.of(
                                SnsPlatform.INSTAGRAM,
                                new StoredContentService.PlatformStoredContentStats(1, 0))));

        service.run(progress);

        verify(batchEventLogger).start("content-sync");
        verify(batchEventLogger).succeeded(eq(batchLogContext), anyMap(), eq(Map.of()));
        verifyNoMoreInteractions(batchEventLogger);
    }

    @Test
    void logsNoTargetsWhenBothContentStagesHaveNoPlatformResults() {
        when(batchEventLogger.start("content-sync")).thenReturn(batchLogContext);
        when(newContentService.collect(any())).thenReturn(
                new NewContentService.NewContentResult(0, 0));
        when(storedContentService.check(any())).thenReturn(
                new StoredContentService.StoredContentResult(0, 0));

        service.run(progress);

        verify(batchEventLogger).start("content-sync");
        verify(batchEventLogger).skipped(
                eq(batchLogContext), eq("NO_TARGETS"), anyMap(), eq(Map.of()));
        verifyNoMoreInteractions(batchEventLogger);
    }

    @Test
    void logsFailureAndRethrowsUnexpectedError() {
        AssertionError failure = new AssertionError("unexpected");
        when(batchEventLogger.start("content-sync")).thenReturn(batchLogContext);
        when(newContentService.collect(any())).thenThrow(failure);

        assertThatThrownBy(() -> service.run(progress)).isSameAs(failure);

        verify(batchEventLogger).start("content-sync");
        verify(batchEventLogger).failed(batchLogContext, failure);
        verifyNoMoreInteractions(batchEventLogger);
    }

    @Test
    void returnsBothSyncStageResultsWithoutRunningStaleInspection() {
        when(newContentService.collect(any())).thenAnswer(invocation -> {
            Consumer<NewContentService.NewContentProgress> callback = progress(invocation);
            callback.accept(new NewContentService.NewContentProgress(1, 0));
            callback.accept(new NewContentService.NewContentProgress(1, 0));
            return new NewContentService.NewContentResult(2, 0);
        });
        when(storedContentService.check(any())).thenAnswer(invocation -> {
            Consumer<StoredContentService.StoredContentProgress> callback = progress(invocation);
            callback.accept(new StoredContentService.StoredContentProgress(3, 0, 0));
            callback.accept(new StoredContentService.StoredContentProgress(3, 1, 0));
            callback.accept(new StoredContentService.StoredContentProgress(3, 2, 1));
            callback.accept(new StoredContentService.StoredContentProgress(3, 3, 1));
            return new StoredContentService.StoredContentResult(0, 1, 3, Map.of());
        });

        ContentBatchService.ContentBatchResult result = service.run(progress);

        InOrder order = inOrder(progress, newContentService, storedContentService);
        order.verify(progress).reportStep("NEW_CONTENT_SYNC", null, 0);
        order.verify(progress).reportStep("STORED_CONTENT_SYNC", null, 0);
        order.verify(progress).describe("신규 콘텐츠 수집 중: 0건 처리");
        order.verify(progress).start("NEW_CONTENT_SYNC", null);
        order.verify(newContentService).collect(any());
        order.verify(progress).reportStep("NEW_CONTENT_SYNC", null, 1);
        order.verify(progress).describe("신규 콘텐츠 수집 중: 1건 처리");
        order.verify(progress).advance(1, 0, 0);
        order.verify(progress).reportStep("NEW_CONTENT_SYNC", null, 2);
        order.verify(progress).describe("신규 콘텐츠 수집 중: 2건 처리");
        order.verify(progress).advance(1, 0, 0);
        order.verify(progress).reportStep("NEW_CONTENT_SYNC", 2L, 2);
        order.verify(storedContentService).check(any());
        order.verify(progress).reportStep("STORED_CONTENT_SYNC", 3L, 0);
        order.verify(progress).describe("기존 콘텐츠 수집 중: 0건 처리");
        order.verify(progress).changeStep("STORED_CONTENT_SYNC");
        order.verify(progress).reportStep("STORED_CONTENT_SYNC", 3L, 1);
        order.verify(progress).describe("기존 콘텐츠 수집 중: 1건 처리");
        order.verify(progress).advance(1, 0, 0);
        order.verify(progress).reportStep("STORED_CONTENT_SYNC", 3L, 2);
        order.verify(progress).describe("기존 콘텐츠 수집 중: 2건 처리");
        order.verify(progress).advance(0, 1, 0);
        order.verify(progress).reportStep("STORED_CONTENT_SYNC", 3L, 3);
        order.verify(progress).describe("기존 콘텐츠 수집 중: 3건 처리");
        order.verify(progress).advance(1, 0, 0);
        order.verify(progress).describe("신규 콘텐츠 2건 수집, 기존 콘텐츠 3건 수집");
        verifyNoInteractions(staleContentInspectionService);
        assertThat(result).isEqualTo(
                new ContentBatchService.ContentBatchResult(2, 0, true, false));
    }

    @Test
    void completesZeroTargetStepsAtStoredTransition() {
        when(newContentService.collect(any())).thenReturn(
                new NewContentService.NewContentResult(0, 0));
        when(storedContentService.check(any())).thenAnswer(invocation -> {
            progress(invocation).accept(new StoredContentService.StoredContentProgress(0, 0, 0));
            return new StoredContentService.StoredContentResult(0, 0, 0, Map.of());
        });

        service.run(progress);

        InOrder order = inOrder(progress, newContentService, storedContentService);
        order.verify(progress).reportStep("NEW_CONTENT_SYNC", null, 0);
        order.verify(progress).reportStep("STORED_CONTENT_SYNC", null, 0);
        order.verify(progress).describe("신규 콘텐츠 수집 중: 0건 처리");
        order.verify(progress).start("NEW_CONTENT_SYNC", null);
        order.verify(newContentService).collect(any());
        order.verify(progress).reportStep("NEW_CONTENT_SYNC", 0L, 0);
        order.verify(storedContentService).check(any());
        order.verify(progress).reportStep("STORED_CONTENT_SYNC", 0L, 0);
        order.verify(progress).describe("기존 콘텐츠 수집 중: 0건 처리");
        order.verify(progress).changeStep("STORED_CONTENT_SYNC");
    }

    @Test
    void transitionsToStoredStepWhenStoredCheckFailsBeforeInitialSnapshot() {
        when(newContentService.collect(any())).thenReturn(
                new NewContentService.NewContentResult(1, 0));
        when(storedContentService.check(any()))
                .thenThrow(new IllegalStateException("query failed"));

        service.run(progress);

        InOrder order = inOrder(progress, newContentService, storedContentService);
        order.verify(newContentService).collect(any());
        order.verify(progress).reportStep("NEW_CONTENT_SYNC", 1L, 1);
        order.verify(storedContentService).check(any());
        order.verify(progress).reportStep("STORED_CONTENT_SYNC", null, 0);
        order.verify(progress).describe("기존 콘텐츠 수집 중: 0건 처리");
        order.verify(progress).changeStep("STORED_CONTENT_SYNC");
        order.verify(progress).advance(0, 1, 0);
    }

    @Test
    void continuesStoredContentCheckWhenNewCollectionFails() {
        when(newContentService.collect(any())).thenThrow(new IllegalStateException("failed"));
        when(storedContentService.check(any())).thenAnswer(invocation -> {
            progress(invocation).accept(new StoredContentService.StoredContentProgress(0, 0, 0));
            return new StoredContentService.StoredContentResult(3, 0);
        });

        ContentBatchService.ContentBatchResult result = service.run(progress);

        InOrder order = inOrder(progress, newContentService, storedContentService);
        order.verify(newContentService).collect(any());
        order.verify(progress).advance(0, 1, 0);
        order.verify(progress).reportStep("NEW_CONTENT_SYNC", 0L, 0);
        order.verify(storedContentService).check(any());
        order.verify(progress).reportStep("STORED_CONTENT_SYNC", 0L, 0);
        order.verify(progress).changeStep("STORED_CONTENT_SYNC");
        assertThat(result).isEqualTo(
                new ContentBatchService.ContentBatchResult(0, 3, false, true));
    }

    @Test
    void preservesSavedCountButMarksNewCollectionFailedForAccountFailures() {
        when(newContentService.collect(any())).thenReturn(
                new NewContentService.NewContentResult(1, 1));
        when(storedContentService.check(any())).thenReturn(
                new StoredContentService.StoredContentResult(3, 0));

        ContentBatchService.ContentBatchResult result = service.run(progress);

        assertThat(result).isEqualTo(
                new ContentBatchService.ContentBatchResult(1, 3, false, true));
    }

    @Test
    void preservesEngagementCountButMarksStoredContentFailedForContentFailures() {
        when(newContentService.collect(any())).thenReturn(
                new NewContentService.NewContentResult(2, 0));
        when(storedContentService.check(any())).thenReturn(
                new StoredContentService.StoredContentResult(3, 1));

        ContentBatchService.ContentBatchResult result = service.run(progress);

        assertThat(result).isEqualTo(
                new ContentBatchService.ContentBatchResult(2, 3, true, false));
    }

    @Test
    void propagatesProgressFailureFromNewContentCallback() {
        IllegalStateException failure = new IllegalStateException("progress failed");
        when(newContentService.collect(any())).thenAnswer(invocation -> {
            progress(invocation).accept(new NewContentService.NewContentProgress(1, 0));
            return new NewContentService.NewContentResult(1, 0);
        });
        doThrow(failure).when(progress).advance(1, 0, 0);

        assertThatThrownBy(() -> service.run(progress)).isSameAs(failure);

        verifyNoInteractions(storedContentService);
    }

    @Test
    void propagatesProgressFailureFromStoredContentCallback() {
        IllegalStateException failure = new IllegalStateException("progress failed");
        when(newContentService.collect(any())).thenReturn(
                new NewContentService.NewContentResult(0, 0));
        when(storedContentService.check(any())).thenAnswer(invocation -> {
            progress(invocation).accept(new StoredContentService.StoredContentProgress(1, 0, 0));
            return new StoredContentService.StoredContentResult(0, 0);
        });
        org.mockito.Mockito.lenient().doThrow(failure).when(progress)
                .reportStep("STORED_CONTENT_SYNC", 1L, 0);

        assertThatThrownBy(() -> service.run(progress)).isSameAs(failure);

        verify(progress, org.mockito.Mockito.never()).advance(0, 1, 0);
    }

    @SuppressWarnings("unchecked")
    private <T> Consumer<T> progress(org.mockito.invocation.InvocationOnMock invocation) {
        return invocation.getArgument(0);
    }

}
