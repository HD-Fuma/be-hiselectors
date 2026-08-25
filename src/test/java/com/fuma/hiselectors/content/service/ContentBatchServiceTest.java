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
import static org.mockito.Mockito.times;
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
import org.mockito.ArgumentCaptor;
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
    void oneAccountWithThreeSavedContentsReportsCumulativeProgressAndFinalTotal() {
        when(newContentService.collect(any())).thenAnswer(invocation -> {
            Consumer<NewContentService.NewContentProgress> callback = progress(invocation);
            callback.accept(new NewContentService.NewContentProgress(1, 0));
            callback.accept(new NewContentService.NewContentProgress(1, 0));
            callback.accept(new NewContentService.NewContentProgress(1, 0));
            return new NewContentService.NewContentResult(3, 0);
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
        order.verify(progress).reportStep("NEW_CONTENT_SYNC", null, 3);
        order.verify(progress).describe("신규 콘텐츠 수집 중: 3건 처리");
        order.verify(progress).advance(1, 0, 0);
        order.verify(progress).reportStep("NEW_CONTENT_SYNC", 3L, 3);
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
        order.verify(progress).describe("신규 콘텐츠 3건 수집, 기존 콘텐츠 3건 수집");
        verifyNoInteractions(staleContentInspectionService);
        assertThat(result).isEqualTo(
                new ContentBatchService.ContentBatchResult(3, 0, true, false));
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

    @Test
    void recordsAtMostThreeOrderedFailuresAndAdditionalCountOnce() {
        when(newContentService.collect(any())).thenAnswer(invocation -> {
            Consumer<NewContentService.NewContentProgress> callback = progress(invocation);
            for (int index = 1; index <= 4; index++) {
                callback.accept(new NewContentService.NewContentProgress(
                        0,
                        1,
                        new ContentSyncFailure(
                                "NEW_CONTENT_SYNC",
                                SnsPlatform.INSTAGRAM,
                                "accountId",
                                "account-" + index + "\n" + "x".repeat(200),
                                "IllegalStateException",
                                "신규 콘텐츠 처리 중 오류가 발생했습니다.")));
            }
            return new NewContentService.NewContentResult(0, 4);
        });
        when(storedContentService.check(any()))
                .thenThrow(new IllegalStateException("https://secret.example/token"));

        service.run(progress);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(progress, times(1)).recordFailure(eq("CONTENT_SYNC_PARTIAL_FAILURE"), message.capture());
        assertThat(message.getValue()).hasSizeLessThanOrEqualTo(500);
        assertThat(message.getValue().lines()).hasSize(4);
        assertThat(message.getValue()).contains("account-1", "account-2", "account-3", "+2건의 추가 실패");
        assertThat(message.getValue()).doesNotContain("account-4", "secret.example", "\r");
    }

    @Test
    void includesDirectCollectAndCheckStageExceptionsInFailureSummary() {
        when(newContentService.collect(any())).thenThrow(new IllegalStateException("new secret"));
        when(storedContentService.check(any())).thenThrow(new IllegalArgumentException("stored secret"));

        service.run(progress);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(progress).recordFailure(eq("CONTENT_SYNC_PARTIAL_FAILURE"), message.capture());
        assertThat(message.getValue().lines()).containsExactly(
                "NEW_CONTENT_SYNC | platform=UNKNOWN | stage=batch | IllegalStateException | 신규 콘텐츠 수집 단계에서 오류가 발생했습니다.",
                "STORED_CONTENT_SYNC | platform=UNKNOWN | stage=batch | IllegalArgumentException | 기존 콘텐츠 확인 단계에서 오류가 발생했습니다.");
        assertThat(message.getValue()).doesNotContain("secret");
    }

    @Test
    void formatterKeepsThreeRepresentativeLinesWhileReservingAdditionalLine() {
        ContentSyncFailure longFailure = new ContentSyncFailure(
                "S".repeat(100),
                SnsPlatform.INSTAGRAM,
                "I".repeat(100),
                "D".repeat(200),
                "E".repeat(200),
                "M".repeat(300));

        String summary = ContentSyncFailureFormatter.format(
                java.util.List.of(longFailure, longFailure, longFailure), 97);

        assertThat(summary).hasSizeLessThanOrEqualTo(500);
        assertThat(summary.lines()).hasSize(4);
        assertThat(summary.lines().toList().getLast()).isEqualTo("+97건의 추가 실패");
        assertThat(summary.lines().limit(3)).allMatch(line -> !line.isBlank());
    }

    @Test
    void normalizesUnicodeSeparatorsAndControlCharactersInIdentifiers() {
        ContentSyncFailure failure = new ContentSyncFailure(
                "NEW_CONTENT_SYNC",
                SnsPlatform.INSTAGRAM,
                "accountId",
                "one\u0085two\u2028three\u2029four\u001B\u0000five",
                "IllegalStateException",
                "신규 콘텐츠 처리 중 오류가 발생했습니다.");

        assertThat(failure.itemId()).isEqualTo("one two three four five");
    }

    @Test
    void keepsIdentifierUtf16WellFormedWhenEmojiCrossesEightyCharacterBoundary() {
        ContentSyncFailure failure = new ContentSyncFailure(
                "NEW_CONTENT_SYNC",
                SnsPlatform.INSTAGRAM,
                "accountId",
                "x".repeat(79) + "😀tail",
                "IllegalStateException",
                "신규 콘텐츠 처리 중 오류가 발생했습니다.");

        assertThat(failure.itemId()).isEqualTo("x".repeat(79));
        assertThat(hasWellFormedSurrogates(failure.itemId())).isTrue();
    }

    @Test
    void keepsFormatterUtf16WellFormedWhenEmojiCrossesFiveHundredCharacterBudget() {
        ContentSyncFailure boundaryFailure = new ContentSyncFailure(
                "S".repeat(40),
                SnsPlatform.INSTAGRAM,
                "I".repeat(40),
                "D".repeat(60) + "😀tail",
                "E",
                "M");

        String summary = ContentSyncFailureFormatter.format(
                java.util.List.of(boundaryFailure, boundaryFailure, boundaryFailure), 0);

        assertThat(summary).hasSizeLessThanOrEqualTo(500);
        assertThat(hasWellFormedSurrogates(summary)).isTrue();
    }

    private boolean hasWellFormedSurrogates(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return false;
                }
                index++;
            } else if (Character.isLowSurrogate(character)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private <T> Consumer<T> progress(org.mockito.invocation.InvocationOnMock invocation) {
        return invocation.getArgument(0);
    }

}
