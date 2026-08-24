package com.fuma.hiselectors.selectors.excellence.service;

import com.fuma.hiselectors.generation.repository.GenerationRepository;
import com.fuma.hiselectors.selectors.excellence.service.SelectorExcellenceGenerationWorker.SelectionResult;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 처리 가능한 종료 기수를 찾아 서로 독립된 트랜잭션으로 선정한다. */
@Slf4j
@Service
public class SelectorExcellenceSelectionService {

    private final GenerationRepository generationRepository;
    private final SelectorExcellenceGenerationWorker generationWorker;
    private final Clock clock;
    private final int graceDays;

    public SelectorExcellenceSelectionService(
            GenerationRepository generationRepository,
            SelectorExcellenceGenerationWorker generationWorker,
            Clock clock,
            @Value("${selectors.excellence.grace-days:7}") int graceDays) {
        if (graceDays < 0) {
            throw new IllegalArgumentException("selectors.excellence.grace-days must not be negative");
        }
        this.generationRepository = generationRepository;
        this.generationWorker = generationWorker;
        this.clock = clock;
        this.graceDays = graceDays;
    }

    public BatchResult selectEligibleGenerations() {
        LocalDateTime asOf = LocalDateTime.now(clock);
        LocalDateTime activityEndCutoffExclusive = asOf.toLocalDate()
                .minusDays(graceDays)
                .plusDays(1)
                .atStartOfDay();
        List<Long> candidateIds = generationRepository
                .findExcellenceSelectionCandidateIds(activityEndCutoffExclusive);

        int processedCount = 0;
        int skippedCount = 0;
        int selectionCount = 0;
        int failedCount = 0;
        for (Long generationId : candidateIds) {
            try {
                SelectionResult result = generationWorker.select(generationId, asOf, graceDays);
                if (result.processed()) {
                    processedCount++;
                    selectionCount += result.selectionCount();
                } else {
                    skippedCount++;
                }
            } catch (RuntimeException exception) {
                failedCount++;
                log.error("셀렉터스 우수 활동자 선정 실패: generationId={}", generationId, exception);
            }
        }

        return new BatchResult(
                candidateIds.size(), processedCount, skippedCount, selectionCount, failedCount);
    }

    public record BatchResult(
            int candidateGenerationCount,
            int processedGenerationCount,
            int skippedGenerationCount,
            int selectionCount,
            int failedGenerationCount
    ) {
    }
}
