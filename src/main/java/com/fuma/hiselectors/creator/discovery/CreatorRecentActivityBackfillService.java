package com.fuma.hiselectors.creator.discovery;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.creator.repository.CreatorDiscoveryInfoRepository;
import com.fuma.hiselectors.creator.repository.CreatorDiscoveryInfoRepository.RecentActivityBackfillTarget;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** 기존 YouTube의 누락된 발굴 정보와 최근 활동 수를 한 번 채운다. */
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
                int changed = fillOrCreate(target.getCreatorId(), count);
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

    private int fillOrCreate(Long creatorId, Integer count) {
        int changed = discoveryInfoRepository.fillRecent90DayContentCount(creatorId, count);
        if (changed == 1) {
            return changed;
        }
        try {
            int inserted = discoveryInfoRepository.insertRecent90DayContentCount(
                    creatorId, count);
            if (inserted == 1) {
                return inserted;
            }
        } catch (DataIntegrityViolationException exception) {
            int raced = discoveryInfoRepository.fillRecent90DayContentCount(creatorId, count);
            if (raced == 1 || discoveryInfoRepository.existsById(creatorId)) {
                return raced;
            }
            throw exception;
        }
        return discoveryInfoRepository.fillRecent90DayContentCount(creatorId, count);
    }

    public record BackfillResult(int candidates, int updated, int failed, int skipped) {
    }
}
