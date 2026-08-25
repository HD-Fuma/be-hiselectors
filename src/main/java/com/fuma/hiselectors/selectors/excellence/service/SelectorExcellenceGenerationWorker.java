package com.fuma.hiselectors.selectors.excellence.service;

import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.repository.GenerationRepository;
import com.fuma.hiselectors.selectors.excellence.model.SelectorExcellenceRewardType;
import com.fuma.hiselectors.selectors.excellence.model.SelectorExcellenceSelection;
import com.fuma.hiselectors.selectors.excellence.model.SelectorExcellenceSelectionType;
import com.fuma.hiselectors.selectors.excellence.repository.SelectorExcellenceQueryRepository;
import com.fuma.hiselectors.selectors.excellence.repository.SelectorExcellenceQueryRepository.SalesCandidate;
import com.fuma.hiselectors.selectors.excellence.repository.SelectorExcellenceSelectionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 한 기수의 선정 결과와 완료 마커를 하나의 독립 트랜잭션으로 기록한다. */
@Service
@RequiredArgsConstructor
public class SelectorExcellenceGenerationWorker {

    static final BigDecimal SALES_THRESHOLD = BigDecimal.valueOf(10_000_000L);
    private static final int RANKING_LIMIT = 3;
    private static final long[] RANKING_REWARDS = {500_000L, 300_000L, 100_000L};
    private static final LocalTime UI_END_OF_DAY = LocalTime.of(23, 59, 59);

    private final GenerationRepository generationRepository;
    private final SelectorExcellenceQueryRepository queryRepository;
    private final SelectorExcellenceSelectionRepository selectionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SelectionResult select(Long generationId, LocalDateTime asOf, int graceDays) {
        Generation generation = generationRepository.findByIdForUpdate(generationId).orElse(null);
        if (generation == null || generation.getSelectorExcellenceSelectedAt() != null) {
            return SelectionResult.skipped();
        }
        if (selectionReadyAt(generation, graceDays).isAfter(asOf)) {
            return SelectionResult.skipped();
        }

        LocalDateTime activityEndExclusive = activityEndExclusive(generation);
        if (queryRepository.hasPendingPurchases(
                generation.getId(), generation.getActivityStartDate(), activityEndExclusive)) {
            return SelectionResult.skipped();
        }
        List<SalesCandidate> candidates = queryRepository.findSalesCandidates(
                        generation.getId(),
                        generation.getActivityStartDate(),
                        activityEndExclusive,
                        asOf)
                .stream()
                .sorted(Comparator.comparing(SalesCandidate::generationSales).reversed()
                        .thenComparing(SalesCandidate::selectorsId))
                .toList();

        List<SelectorExcellenceSelection> selections = new ArrayList<>();
        for (SalesCandidate candidate : candidates) {
            if (candidate.generationSales().compareTo(SALES_THRESHOLD) >= 0) {
                selections.add(SelectorExcellenceSelection.create(
                        generation.getId(),
                        candidate.selectorsId(),
                        SelectorExcellenceSelectionType.SALES_THRESHOLD,
                        candidate.generationSales(),
                        candidate.confirmedOrderCount(),
                        null,
                        SelectorExcellenceRewardType.DISCOUNT_COUPON,
                        50L,
                        2,
                        asOf));
            }
        }

        int rankingSize = Math.min(RANKING_LIMIT, candidates.size());
        for (int index = 0; index < rankingSize; index++) {
            SalesCandidate candidate = candidates.get(index);
            int rankNo = index + 1;
            selections.add(SelectorExcellenceSelection.create(
                    generation.getId(),
                    candidate.selectorsId(),
                    SelectorExcellenceSelectionType.SALES_RANKING,
                    candidate.generationSales(),
                    candidate.confirmedOrderCount(),
                    rankNo,
                    SelectorExcellenceRewardType.H_POINT,
                    RANKING_REWARDS[index],
                    1,
                    asOf));
        }

        selectionRepository.saveAll(selections);
        generation.markSelectorExcellenceSelected(asOf);
        return SelectionResult.completed(selections.size());
    }

    static LocalDateTime selectionReadyAt(Generation generation, int graceDays) {
        if (graceDays < 0) {
            throw new IllegalArgumentException("graceDays must not be negative");
        }
        LocalDateTime graceReadyAt = generation.getActivityEndDate()
                .toLocalDate()
                .plusDays(graceDays)
                .atStartOfDay();
        return graceReadyAt.isBefore(generation.getActivityEndDate())
                ? generation.getActivityEndDate()
                : graceReadyAt;
    }

    static LocalDateTime activityEndExclusive(Generation generation) {
        LocalDateTime activityEnd = generation.getActivityEndDate();
        if (activityEnd.toLocalTime().equals(UI_END_OF_DAY)) {
            return activityEnd.toLocalDate().plusDays(1).atStartOfDay();
        }
        return activityEnd.plusNanos(1_000);
    }

    public record SelectionResult(boolean processed, int selectionCount) {

        static SelectionResult completed(int selectionCount) {
            return new SelectionResult(true, selectionCount);
        }

        static SelectionResult skipped() {
            return new SelectionResult(false, 0);
        }
    }
}
