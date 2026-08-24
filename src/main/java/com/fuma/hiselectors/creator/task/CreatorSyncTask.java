package com.fuma.hiselectors.creator.task;

import com.fuma.hiselectors.creator.discovery.batch.InstagramDiscoveryBatchResult;
import com.fuma.hiselectors.creator.discovery.batch.InstagramDiscoveryBatchService;
import com.fuma.hiselectors.creator.discovery.scheduler.YoutubeDiscoveryBatchResult;
import com.fuma.hiselectors.creator.discovery.scheduler.YoutubeDiscoveryBatchService;
import com.fuma.hiselectors.taskrun.service.TaskExecutionContext;
import com.fuma.hiselectors.taskrun.service.TrackedTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CreatorSyncTask implements TrackedTask {

    private final YoutubeDiscoveryBatchService youtube;
    private final InstagramDiscoveryBatchService instagram;

    @Override
    public void execute(TaskExecutionContext context) {
        context.progress().start("YOUTUBE_CREATOR_SYNC", null);
        int[] youtubeCounts = new int[2];
        YoutubeDiscoveryBatchResult youtubeResult = youtube.runYoutubeOnly(snapshot -> {
            context.progress().describe("%d개 키워드 중 %d개 처리 · 크리에이터 %d명 수집".formatted(
                    snapshot.targetKeywords(), snapshot.attemptedKeywords(),
                    snapshot.uniqueCollectedCreators()));
            context.progress().advance(
                    snapshot.succeededKeywords() - youtubeCounts[0],
                    snapshot.failedKeywords() - youtubeCounts[1], 0);
            youtubeCounts[0] = snapshot.succeededKeywords();
            youtubeCounts[1] = snapshot.failedKeywords();
        });

        context.progress().describe("Instagram 크리에이터 수집 준비 중");
        context.progress().changeStep("INSTAGRAM_CREATOR_SYNC");
        int[] instagramCounts = new int[2];
        InstagramDiscoveryBatchResult instagramResult = instagram.run(snapshot -> {
            context.progress().describe("%d명 중 %d명 처리 · 크리에이터 %d명 수집".formatted(
                    snapshot.targetCreators(), snapshot.attemptedCreators(),
                    snapshot.uniqueCollectedCreators()));
            context.progress().advance(
                    snapshot.succeededCreators() - instagramCounts[0],
                    snapshot.failedCreators() - instagramCounts[1], 0);
            instagramCounts[0] = snapshot.succeededCreators();
            instagramCounts[1] = snapshot.failedCreators();
        });

        context.progress().describe("YouTube %d명 · Instagram %d명 수집".formatted(
                youtubeResult.uniqueCollectedCreators(),
                instagramResult.uniqueCollectedCreators()));
    }
}
