package com.fuma.hiselectors.selectors.service;

import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.repository.GenerationRepository;
import com.fuma.hiselectors.penalty.model.PenaltyStatus;
import com.fuma.hiselectors.penalty.model.PenaltySource;
import com.fuma.hiselectors.penalty.repository.PenaltyHistoryRepository;
import com.fuma.hiselectors.purchase.model.PurchaseStatus;
import com.fuma.hiselectors.purchase.repository.PurchaseHistoryRepository;
import com.fuma.hiselectors.selectors.dto.SelectorsGenerationResponse;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.model.SelectorsGeneration;
import com.fuma.hiselectors.selectors.repository.SelectorsGenerationRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SelectorsLifecycleService {

    private static final BigDecimal RENEWAL_THRESHOLD = BigDecimal.valueOf(500_000);

    private final GenerationRepository generationRepository;
    private final SelectorsGenerationRepository selectorsGenerationRepository;
    private final SelectorsRepository selectorsRepository;
    private final PurchaseHistoryRepository purchaseHistoryRepository;
    private final PenaltyHistoryRepository penaltyHistoryRepository;
    private final Clock clock;

    @Transactional
    public int enrollQualifiedSelectors() {
        generationRepository.findAllForRead();
        LocalDateTime now = LocalDateTime.now(clock);
        int enrolled = 0;
        // ponytail: 종료 기수를 매일 재검사한다. 기수 수가 커지면 처리 시각 컬럼으로 범위를 줄인다.
        for (Generation generation : generationRepository
                .findAllByActivityEndDateLessThanOrderByActivityEndDateAsc(now)) {
            Generation next = generationRepository
                    .findFirstByActivityStartDateGreaterThanOrderByActivityStartDateAscIdAsc(
                            generation.getActivityEndDate())
                    .orElse(null);
            for (SelectorsGeneration membership : selectorsGenerationRepository
                    .findAllByGenerationId(generation.getId())) {
                Selectors selectors = selectorsRepository
                        .findByIdForUpdate(membership.getSelectorsId()).orElse(null);
                if (selectors == null) {
                    continue;
                }
                penaltyHistoryRepository.findAllBySelectorsIdAndGenerationIdAndStatus(
                                selectors.getId(), generation.getId(), PenaltyStatus.ACTIVE)
                        .stream()
                        .filter(penalty -> penalty.getSource() != PenaltySource.MANUAL)
                        .forEach(penalty -> penalty.release(now));
                if (selectors.isBlacklisted()) {
                    continue;
                }
                if (selectors.isDeleted()) {
                    continue;
                }
                if (next != null
                        && !next.getActivityEndDate().isBefore(now)
                        && !selectorsGenerationRepository
                        .existsBySelectorsIdAndGenerationId(selectors.getId(), next.getId())) {
                    BigDecimal sales = purchaseHistoryRepository.sumConfirmedSalesByConfirmedAt(
                            selectors.getId(), PurchaseStatus.PURCHASE_CONFIRMED,
                            generation.getActivityStartDate(),
                            generation.getActivityEndDate().plusNanos(1_000));
                    if (sales != null && sales.compareTo(RENEWAL_THRESHOLD) >= 0) {
                        selectorsGenerationRepository.save(SelectorsGeneration.builder()
                                .selectorsId(selectors.getId())
                                .generationId(next.getId())
                                .build());
                        selectors.activate();
                        enrolled++;
                        continue;
                    }
                }
                if (!hasCurrentMembership(selectors.getId(), now)) {
                    selectors.deactivate();
                }
            }
        }
        return enrolled;
    }

    private boolean hasCurrentMembership(Long selectorsId, LocalDateTime now) {
        return selectorsGenerationRepository.findGenerationsOf(selectorsId).stream()
                .anyMatch(generation -> isCurrent(generation, now));
    }

    private boolean isCurrent(SelectorsGenerationResponse generation, LocalDateTime now) {
        return generation.joinedAt() != null
                && generation.activityEndDate() != null
                && !generation.joinedAt().isAfter(now)
                && !generation.activityEndDate().isBefore(now);
    }
}
