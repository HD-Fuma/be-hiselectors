package com.fuma.hiselectors.creator.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.fuma.hiselectors.creator.discovery.batch.InstagramDiscoveryBatchResult;
import com.fuma.hiselectors.creator.discovery.batch.InstagramDiscoveryBatchService;
import com.fuma.hiselectors.creator.discovery.scheduler.YoutubeDiscoveryBatchResult;
import com.fuma.hiselectors.creator.discovery.scheduler.YoutubeDiscoveryBatchService;
import com.fuma.hiselectors.taskrun.service.TaskExecutionContext;
import com.fuma.hiselectors.taskrun.service.TaskLease;
import com.fuma.hiselectors.taskrun.service.TaskProgressReporter;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class CreatorSyncTaskTest {

    @Test
    void reportsPlatformSnapshotsAsMessagesAndCountDeltas() {
        YoutubeDiscoveryBatchService youtube = mock(YoutubeDiscoveryBatchService.class);
        InstagramDiscoveryBatchService instagram = mock(InstagramDiscoveryBatchService.class);
        TaskProgressReporter progress = mock(TaskProgressReporter.class);

        YoutubeDiscoveryBatchResult youtubeFirst = youtubeResult(2, 1, 1, 0, 3);
        YoutubeDiscoveryBatchResult youtubeFinal = youtubeResult(2, 2, 1, 1, 4);
        doAnswer(invocation -> {
            Consumer<YoutubeDiscoveryBatchResult> callback = invocation.getArgument(0);
            callback.accept(youtubeFirst);
            callback.accept(youtubeFinal);
            return youtubeFinal;
        }).when(youtube).runYoutubeOnly(any());

        InstagramDiscoveryBatchResult instagramFirst = instagramResult(4, 1, 1, 0, 1);
        InstagramDiscoveryBatchResult instagramSecond = instagramResult(4, 2, 1, 1, 1);
        InstagramDiscoveryBatchResult instagramFinal = instagramResult(4, 4, 3, 1, 3);
        doAnswer(invocation -> {
            Consumer<InstagramDiscoveryBatchResult> callback = invocation.getArgument(0);
            callback.accept(instagramFirst);
            callback.accept(instagramSecond);
            callback.accept(instagramFinal);
            return instagramFinal;
        }).when(instagram).run(any());

        new CreatorSyncTask(youtube, instagram)
                .execute(new TaskExecutionContext(mock(TaskLease.class), progress));

        InOrder order = inOrder(progress, youtube, instagram);
        order.verify(progress).start("YOUTUBE_CREATOR_SYNC", null);
        order.verify(youtube).runYoutubeOnly(any());
        order.verify(progress).describe("2개 키워드 중 1개 처리 · 크리에이터 3명 수집");
        order.verify(progress).advance(1, 0, 0);
        order.verify(progress).describe("2개 키워드 중 2개 처리 · 크리에이터 4명 수집");
        order.verify(progress).advance(0, 1, 0);
        order.verify(progress).describe("Instagram 크리에이터 수집 준비 중");
        order.verify(progress).changeStep("INSTAGRAM_CREATOR_SYNC");
        order.verify(instagram).run(any());
        order.verify(progress).describe("4명 중 1명 처리 · 크리에이터 1명 수집");
        order.verify(progress).advance(1, 0, 0);
        order.verify(progress).describe("4명 중 2명 처리 · 크리에이터 1명 수집");
        order.verify(progress).advance(0, 1, 0);
        order.verify(progress).describe("4명 중 4명 처리 · 크리에이터 3명 수집");
        order.verify(progress).advance(2, 0, 0);
        order.verify(progress).describe("YouTube 4명 · Instagram 3명 수집");
        order.verifyNoMoreInteractions();
    }

    private YoutubeDiscoveryBatchResult youtubeResult(
            int target, int attempted, int succeeded, int failed, int uniqueCollected) {
        return new YoutubeDiscoveryBatchResult(
                target, target, attempted, succeeded, failed,
                attempted * 102, attempted * 50, uniqueCollected,
                uniqueCollected, 0, uniqueCollected);
    }

    private InstagramDiscoveryBatchResult instagramResult(
            int target, int attempted, int succeeded, int failed, int uniqueCollected) {
        return new InstagramDiscoveryBatchResult(
                target, attempted, succeeded, failed,
                uniqueCollected, 0, uniqueCollected);
    }
}
