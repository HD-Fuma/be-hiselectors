package com.fuma.hiselectors.creator.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.fuma.hiselectors.creator.discovery.batch.InstagramDiscoveryBatchResult;
import com.fuma.hiselectors.creator.discovery.batch.InstagramDiscoveryBatchService;
import com.fuma.hiselectors.taskrun.service.TaskExecutionContext;
import com.fuma.hiselectors.taskrun.service.TaskLease;
import com.fuma.hiselectors.taskrun.service.TaskProgressReporter;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class InstagramCreatorSyncTaskTest {

    @Test
    void reportsSnapshotsAsMessagesAndCountDeltas() {
        InstagramDiscoveryBatchService instagram = mock(InstagramDiscoveryBatchService.class);
        TaskProgressReporter progress = mock(TaskProgressReporter.class);
        InstagramDiscoveryBatchResult first = result(3, 1, 1, 0, 1);
        InstagramDiscoveryBatchResult second = result(3, 2, 1, 1, 1);
        InstagramDiscoveryBatchResult last = result(3, 3, 2, 1, 2);
        doAnswer(invocation -> {
            Consumer<InstagramDiscoveryBatchResult> callback = invocation.getArgument(0);
            callback.accept(first);
            callback.accept(second);
            callback.accept(last);
            return last;
        }).when(instagram).run(any());

        new InstagramCreatorSyncTask(instagram)
                .execute(new TaskExecutionContext(mock(TaskLease.class), progress));

        InOrder order = inOrder(progress, instagram);
        order.verify(progress).start("INSTAGRAM_CREATOR_SYNC", null);
        order.verify(instagram).run(any());
        order.verify(progress).describe("3명 중 1명 처리 · 크리에이터 1명 수집");
        order.verify(progress).advance(1, 0, 0);
        order.verify(progress).describe("3명 중 2명 처리 · 크리에이터 1명 수집");
        order.verify(progress).advance(0, 1, 0);
        order.verify(progress).describe("3명 중 3명 처리 · 크리에이터 2명 수집");
        order.verify(progress).advance(1, 0, 0);
        order.verify(progress).describe("Instagram 2명 수집");
        order.verifyNoMoreInteractions();
    }

    @Test
    void reportsZeroWhenThereAreNoInstagramCreators() {
        InstagramDiscoveryBatchService instagram = mock(InstagramDiscoveryBatchService.class);
        TaskProgressReporter progress = mock(TaskProgressReporter.class);
        InstagramDiscoveryBatchResult empty = result(0, 0, 0, 0, 0);
        doAnswer(invocation -> empty).when(instagram).run(any());

        new InstagramCreatorSyncTask(instagram)
                .execute(new TaskExecutionContext(mock(TaskLease.class), progress));

        InOrder order = inOrder(progress, instagram);
        order.verify(progress).start("INSTAGRAM_CREATOR_SYNC", null);
        order.verify(instagram).run(any());
        order.verify(progress).describe("Instagram 0명 수집");
        order.verifyNoMoreInteractions();
    }

    private InstagramDiscoveryBatchResult result(
            int target, int attempted, int succeeded, int failed, int uniqueCollected) {
        return new InstagramDiscoveryBatchResult(
                target, attempted, succeeded, failed,
                uniqueCollected, 0, uniqueCollected);
    }
}
