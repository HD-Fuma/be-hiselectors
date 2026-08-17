package com.fuma.hiselectors.penalty.service;

import com.fuma.hiselectors.inspection.model.ViolationStatus;
import com.fuma.hiselectors.inspection.repository.ViolationItemRepository;
import com.fuma.hiselectors.penalty.model.PenaltyHistory;
import com.fuma.hiselectors.penalty.model.PenaltyStatus;
import com.fuma.hiselectors.penalty.repository.PenaltyHistoryRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PenaltyService {

    private static final Set<ViolationStatus> OPEN_STATUSES = EnumSet.of(
            ViolationStatus.PENDING,
            ViolationStatus.VIOLATION_CONFIRMED,
            ViolationStatus.EDIT_REQUESTED);

    private final PenaltyHistoryRepository penaltyHistoryRepository;
    private final ViolationItemRepository violationItemRepository;
    private final Clock clock;

    @Transactional
    public boolean activateIfAbsent(Long selectorsId, Long violationTypeId) {
        if (penaltyHistoryRepository.findFirstBySelectorsIdAndStatusOrderByIdDesc(
                selectorsId, PenaltyStatus.ACTIVE).isPresent()) {
            return false;
        }
        penaltyHistoryRepository.save(PenaltyHistory.activate(
                selectorsId, violationTypeId, LocalDateTime.now(clock)));
        return true;
    }

    @Transactional
    public boolean releaseIfEligible(Long selectorsId) {
        if (violationItemRepository.existsOpenBySelectorsId(selectorsId, OPEN_STATUSES)) {
            return false;
        }
        return penaltyHistoryRepository.findFirstBySelectorsIdAndStatusOrderByIdDesc(
                        selectorsId, PenaltyStatus.ACTIVE)
                .map(penalty -> {
                    penalty.release(LocalDateTime.now(clock));
                    return true;
                })
                .orElse(false);
    }
}
