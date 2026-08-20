package com.fuma.hiselectors.penalty.service;

import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.repository.ViolationTypeRepository;
import com.fuma.hiselectors.penalty.dto.PenaltyCreateRequest;
import com.fuma.hiselectors.penalty.model.PenaltyHistory;
import com.fuma.hiselectors.penalty.repository.PenaltyHistoryRepository;
import com.fuma.hiselectors.selectors.dto.PenaltyHistoryResponse;
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.repository.SelectorsGenerationRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PenaltyService {

    private static final long BLACKLIST_THRESHOLD = 3;

    private final SelectorsRepository selectorsRepository;
    private final SelectorsGenerationRepository selectorsGenerationRepository;
    private final PenaltyHistoryRepository penaltyHistoryRepository;
    private final ViolationTypeRepository violationTypeRepository;
    private final Clock clock;

    @Transactional
    public PenaltyHistoryResponse create(Long selectorsId, PenaltyCreateRequest request) {
        if (!violationTypeRepository.existsById(request.violationTypeId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Selectors selectors = selectorsRepository.findByIdForUpdate(selectorsId)
                .filter(value -> !value.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_NOT_FOUND));
        if (selectors.isBlacklisted()) {
            throw new BusinessException(ErrorCode.BLACKLISTED_SELECTOR);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        var generation = selectorsGenerationRepository
                .findGenerationsOf(selectorsId).stream()
                .filter(value -> value.joinedAt() != null
                        && value.activityEndDate() != null
                        && !value.joinedAt().isAfter(now)
                        && !value.activityEndDate().isBefore(now))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED));
        PenaltyHistory saved = penaltyHistoryRepository.save(PenaltyHistory.activate(
                selectorsId, generation.generationId(), request.violationTypeId(), now));
        if (penaltyHistoryRepository.countBySelectorsIdAndGenerationId(
                selectorsId, generation.generationId()) >= BLACKLIST_THRESHOLD) {
            selectors.blacklist();
        }
        return PenaltyHistoryResponse.from(saved);
    }
}
