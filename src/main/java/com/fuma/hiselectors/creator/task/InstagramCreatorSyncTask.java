package com.fuma.hiselectors.creator.task;

import com.fuma.hiselectors.creator.discovery.batch.InstagramDiscoveryBatchResult;
import com.fuma.hiselectors.creator.discovery.batch.InstagramDiscoveryBatchService;
import com.fuma.hiselectors.taskrun.service.TaskExecutionContext;
import com.fuma.hiselectors.taskrun.service.TrackedTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InstagramCreatorSyncTask implements TrackedTask {

    private final InstagramDiscoveryBatchService instagram;

    @Override
    public void execute(TaskExecutionContext context) {
        context.progress().start("INSTAGRAM_CREATOR_SYNC", null);
        int[] counts = new int[2];
        InstagramDiscoveryBatchResult result = instagram.run(snapshot -> {
            context.progress().describe("%d명 중 %d명 처리 · 크리에이터 %d명 수집".formatted(
                    snapshot.targetCreators(), snapshot.attemptedCreators(),
                    snapshot.uniqueCollectedCreators()));
            context.progress().advance(
                    snapshot.succeededCreators() - counts[0],
                    snapshot.failedCreators() - counts[1], 0);
            counts[0] = snapshot.succeededCreators();
            counts[1] = snapshot.failedCreators();
        });

        context.progress().describe("Instagram %d명 수집".formatted(
                result.uniqueCollectedCreators()));
    }
}
