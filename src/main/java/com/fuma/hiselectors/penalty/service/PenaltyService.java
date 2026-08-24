package com.fuma.hiselectors.penalty.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.model.ViolationStatus;
import com.fuma.hiselectors.inspection.repository.ViolationItemRepository;
import com.fuma.hiselectors.inspection.repository.ViolationTypeRepository;
import com.fuma.hiselectors.penalty.dto.PenaltyCreateRequest;
import com.fuma.hiselectors.penalty.model.PenaltyHistory;
import com.fuma.hiselectors.penalty.model.PenaltyStatus;
import com.fuma.hiselectors.penalty.repository.PenaltyHistoryRepository;
import com.fuma.hiselectors.selectors.dto.PenaltyHistoryResponse;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.dto.SelectorsGenerationResponse;
import com.fuma.hiselectors.selectors.repository.SelectorsGenerationRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
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

    private static final long BLACKLIST_THRESHOLD = 3;
    private static final Set<ViolationStatus> OPEN_STATUSES = EnumSet.of(
            ViolationStatus.PENDING,
            ViolationStatus.VIOLATION_CONFIRMED,
            ViolationStatus.EDIT_REQUESTED);

    private final SelectorsRepository selectorsRepository;
    private final SelectorsGenerationRepository selectorsGenerationRepository;
    private final PenaltyHistoryRepository penaltyHistoryRepository;
    private final ViolationTypeRepository violationTypeRepository;
    private final ViolationItemRepository violationItemRepository;
    private final Clock clock;

    @Transactional
    public PenaltyHistoryResponse create(Long selectorsId, PenaltyCreateRequest request) {
        if (!violationTypeRepository.existsById(request.violationTypeId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Selectors selectors = requireSelectorsForUpdate(selectorsId);
        if (selectors.isBlacklisted()) {
            throw new BusinessException(ErrorCode.BLACKLISTED_SELECTOR);
        }
        if (findActivePenalty(selectorsId).isPresent()) {
            throw new BusinessException(ErrorCode.ACTIVE_PENALTY_ALREADY_EXISTS);
        }
        return PenaltyHistoryResponse.from(activate(
                selectors, request.violationTypeId(), LocalDateTime.now(clock)));
    }

    /** 위반 확정 시 활성 패널티가 없을 때만 새 패널티 주기를 시작한다. */
    @Transactional
    public boolean activateIfAbsent(Long selectorsId, Long violationTypeId) {
        Selectors selectors = requireSelectorsForUpdate(selectorsId);
        if (selectors.isBlacklisted() || findActivePenalty(selectorsId).isPresent()) {
            return false;
        }
        activate(selectors, violationTypeId, LocalDateTime.now(clock));
        return true;
    }

    /** 열린 위반이 모두 해소되면 현재 패널티 주기를 종료한다. */
    @Transactional
    public boolean releaseIfEligible(Long selectorsId) {
        if (violationItemRepository.existsOpenBySelectorsId(selectorsId, OPEN_STATUSES)) {
            return false;
        }
        requireSelectorsForUpdate(selectorsId);
        return findActivePenalty(selectorsId).map(penalty -> {
            penalty.release(LocalDateTime.now(clock));
            return true;
        }).orElse(false);
    }

    private PenaltyHistory activate(Selectors selectors, Long violationTypeId,
                                    LocalDateTime now) {
        SelectorsGenerationResponse generation = currentGeneration(selectors.getId(), now);
        PenaltyHistory saved = penaltyHistoryRepository.saveAndFlush(PenaltyHistory.activate(
                selectors.getId(), generation.generationId(), violationTypeId, now));
        long accumulatedPenaltyCount = penaltyHistoryRepository.countBySelectorsId(
                selectors.getId());
        if (accumulatedPenaltyCount >= BLACKLIST_THRESHOLD) {
            selectors.blacklist();
        }
        return saved;
    }

    private Selectors requireSelectorsForUpdate(Long selectorsId) {
        return selectorsRepository.findByIdForUpdate(selectorsId)
                .filter(value -> !value.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_NOT_FOUND));
    }

    private java.util.Optional<PenaltyHistory> findActivePenalty(Long selectorsId) {
        return penaltyHistoryRepository.findFirstBySelectorsIdAndStatusOrderByIdDesc(
                selectorsId, PenaltyStatus.ACTIVE);
    }

    private SelectorsGenerationResponse currentGeneration(Long selectorsId, LocalDateTime now) {
        return selectorsGenerationRepository.findGenerationsOf(selectorsId).stream()
                .filter(value -> value.joinedAt() != null
                        && value.activityStartDate() != null
                        && value.activityEndDate() != null
                        && !value.joinedAt().isAfter(now)
                        && !value.activityStartDate().isAfter(now)
                        && !value.activityEndDate().isBefore(now))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED));
    }
}
