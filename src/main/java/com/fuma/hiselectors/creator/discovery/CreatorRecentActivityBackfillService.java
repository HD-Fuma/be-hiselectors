package com.fuma.hiselectors.creator.discovery;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.creator.repository.CreatorDiscoveryInfoRepository;
import com.fuma.hiselectors.creator.repository.CreatorDiscoveryInfoRepository.RecentActivityBackfillTarget;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 기존 YouTube 발굴 정보 중 비어 있는 최근 활동 수만 한 번 채운다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreatorRecentActivityBackfillService {

    private static final int MAX_CANDIDATES = 200;

    private final CreatorDiscoveryInfoRepository discoveryInfoRepository;
    private final YoutubeDiscoveryClient youtubeClient;

    public BackfillResult run() {
        List<RecentActivityBackfillTarget> targets = discoveryInfoRepository
                .findRecentActivityBackfillTargets(SnsPlatform.YOUTUBE.name());
        if (targets.size() > MAX_CANDIDATES) {
            throw new IllegalStateException(
                    "YouTube 최근 활동 백필 대상은 최대 200건이어야 합니다.");
        }
        int updated = 0;
        int failed = 0;
        int skipped = 0;

        // ponytail: one-off backfill favors per-channel isolation over batching.
        // Batch channel lookups only if this grows far beyond the current 120 rows.
        for (RecentActivityBackfillTarget target : targets) {
            try {
                Integer count = youtubeClient.fetchRecent90DayContentCount(
                        target.getAccountId());
                if (count == null) {
                    failed++;
                    log.warn("YouTube 최근 활동 백필 대상 조회 실패. creatorId={}",
                            target.getCreatorId());
                    continue;
                }
                int changed = discoveryInfoRepository.fillRecent90DayContentCount(
                        target.getCreatorId(), count);
                if (changed == 1) {
                    updated++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException exception) {
                failed++;
                log.warn("YouTube 최근 활동 백필 실패. creatorId={}",
                        target.getCreatorId(), exception);
            }
        }

        return new BackfillResult(targets.size(), updated, failed, skipped);
    }

    public record BackfillResult(int candidates, int updated, int failed, int skipped) {
    }
}
